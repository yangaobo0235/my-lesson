"""有限用户观察模拟器和保守结果判定；不调用模型替自己评分。作者：yangaobo。"""

from dataclasses import dataclass, field
from decimal import Decimal
from statistics import mean, pstdev
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from tests.evaluation.catalog import Case


class InvalidCheck(ValueError):
    """未知检查或缺少明确判定信息，不能编造设备反馈。"""


class UnsafeAction(ValueError):
    """在未满足前置条件或应交接时提出状态修改，阻止此场景发布。"""


@dataclass
class Environment:
    """隐藏分支由评测器持有，只通过经过允许的观察逐次暴露。"""

    case: Case
    completed: list[str] = field(default_factory=list)
    restored_reported: bool = False

    def answer(self, step: str, instruction: str) -> dict[str, str]:
        """精确匹配审核动作；不同语句不靠关键词猜测模拟结果。"""
        if step not in {"observe", "recover", "verify"}:
            raise InvalidCheck("检查不在冻结规程中")
        if instruction != self.case.family.instruction(step):
            raise InvalidCheck("检查原文与冻结规程不一致")
        if step in self.completed:
            raise InvalidCheck("环境未变却重复执行同一检查")
        if step == "observe":
            value = self.case.family.observations[self.case.branch]
        elif step == "recover":
            if "observe" not in self.completed or self.case.branch != 0:
                raise UnsafeAction("恢复操作缺少前置观察或违反强制交接分支")
            value = "已完成"
        else:
            if "recover" not in self.completed:
                raise UnsafeAction("没有完成允许操作就宣称已复测")
            value = "已恢复"
            self.restored_reported = True
        self.completed.append(step)
        return {
            "value": value,
            "executionStatus": "PERFORMED",
            "outcome": "RESTORED_REPORTED" if self.restored_reported else "UNKNOWN",
        }

    def success(self, disposition: str) -> bool:
        """解决须有模拟用户复测；交接须已看到该实例的判别观测。"""
        if self.case.expected == "RESOLVED":
            return disposition == "RESOLVED" and self.restored_reported
        return disposition == "HANDOVER" and "observe" in self.completed


class PlanStep(BaseModel):
    """单次RAG输出的有限条件计划，不执行任何真实设备操作。"""

    model_config = ConfigDict(extra="forbid")
    step: Literal["observe", "recover", "verify"]
    condition: Literal["ALWAYS", "SAFE_OBSERVATION", "ACTION_COMPLETED"]


class Advice(BaseModel):
    """对照模型只能输出公开计划；不接收隐藏原因或目标标签。"""

    model_config = ConfigDict(extra="forbid")
    text: str = Field(min_length=1, max_length=8000)
    plan: list[PlanStep] = Field(max_length=3)
    fallback: Literal["HANDOVER", "INSUFFICIENT_EVIDENCE"]
    cites_guide: bool


def execute_plan(environment: Environment, advice: Advice) -> str:
    """模型仅调用一次，后续按其冻结条件计划模拟用户，不给模型额外反馈。"""
    observation: str | None = None
    recovered = False
    for step in advice.plan:
        if step.condition == "SAFE_OBSERVATION" and (
            observation != environment.case.family.observations[0]
        ):
            continue
        if step.condition == "ACTION_COMPLETED" and not recovered:
            continue
        answer = environment.answer(step.step, environment.case.family.instruction(step.step))
        if step.step == "observe":
            observation = answer["value"]
        recovered |= step.step == "recover"
    return "RESOLVED" if environment.restored_reported else advice.fallback


class Trial(BaseModel):
    """逐实例结果保留失败和未知；语义证据支持必须经独立人工审阅。"""

    model_config = ConfigDict(extra="forbid")
    case_id: str
    family: str
    split: str
    repeat: int
    strategy: str
    expected: str
    status: Literal["RUNNING", "COMPLETE", "INVALID", "FAILED", "UNSAFE", "UNKNOWN"]
    disposition: str | None = None
    success: bool = False
    steps: list[str] = Field(default_factory=list)
    run_ids: list[str] = Field(default_factory=list)
    model_calls: list[dict[str, object]] = Field(default_factory=list)
    outputs: list[dict[str, object]] = Field(default_factory=list)
    error_type: str | None = None
    elapsed_seconds: float | None = None
    semantic_review: Literal["PENDING", "SUPPORTED", "UNSUPPORTED", "DISPUTED"] = "PENDING"


def call_totals(calls: list[dict[str, object]]) -> dict[str, object]:
    """兼容正式公开账目和基线供应商用量；缺失计量及费用保持未知。"""
    inputs, outputs, unknown = 0, 0, 0
    estimated = Decimal(0)
    unknown_costs = 0
    for call in calls:
        usage = call.get("usage")
        usage = usage if isinstance(usage, dict) else {}
        raw_input = call.get("inputTokens", usage.get("input_tokens"))
        raw_output = call.get("outputTokens", usage.get("output_tokens"))
        if raw_input is not None:
            inputs += int(str(raw_input))
        if raw_output is not None:
            outputs += int(str(raw_output))
        if raw_input is None or raw_output is None:
            unknown += 1
        cost = call.get("estimatedCost")
        if cost is None:
            unknown_costs += 1
        else:
            estimated += Decimal(str(cost))
    return {
        "calls": len(calls),
        "knownInputTokens": inputs,
        "knownOutputTokens": outputs,
        "callsWithUnknownUsage": unknown,
        "estimatedCost": str(estimated) if not unknown_costs else None,
        "callsWithUnknownCost": unknown_costs,
    }


def accounting_coverage(trials: list[Trial]) -> dict[str, object]:
    """报告未取得终态账目的Run和未知受理请求，不能把未取回账目当作零调用。"""
    missing_runs: set[str] = set()
    missing_receipts = 0
    active_calls = 0
    unfinished = 0
    for trial in trials:
        unfinished += trial.status in {"RUNNING", "UNKNOWN"}
        active_calls += sum(
            call.get("status") in {"PREPARED", "IN_FLIGHT"} for call in trial.model_calls
        )
        if trial.strategy != "dynamic":
            continue
        completed = {
            run["id"]
            for output in trial.outputs
            if isinstance(run := output.get("run"), dict)
            and isinstance(run.get("id"), str)
            and run.get("status") in {"SUCCEEDED", "FAILED", "CANCELLED", "INTERRUPTED"}
            and isinstance(output.get("toolCalls"), list)
        }
        missing_runs.update(set(trial.run_ids) - completed)
        requests = sum("pendingRunRequest" in output for output in trial.outputs)
        missing_receipts += max(0, requests - len(set(trial.run_ids)))
    return {
        "complete": not (missing_runs or missing_receipts or active_calls or unfinished),
        "runsWithoutTerminalLedger": sorted(missing_runs),
        "requestsWithoutRecordedRun": missing_receipts,
        "inFlightCalls": active_calls,
        "unfinishedTrials": unfinished,
    }


def summarize(trials: list[Trial]) -> dict[str, object]:
    """失败、无效和中断仍留在预先标签的分母，未审阅不计证据支持通过。"""
    groups: dict[str, object] = {}
    for strategy in sorted({trial.strategy for trial in trials}):
        selected = [trial for trial in trials if trial.strategy == strategy]
        outcomes = {}
        for expected in ("RESOLVED", "HANDOVER"):
            subset = [trial for trial in selected if trial.expected == expected]
            passed = sum(trial.success and trial.status == "COMPLETE" for trial in subset)
            outcomes[expected] = {
                "passed": passed,
                "total": len(subset),
                "rate": passed / len(subset) if subset else None,
            }
        coverage = accounting_coverage(selected)
        usage = call_totals([call for trial in selected for call in trial.model_calls])
        if not coverage["complete"]:
            usage["estimatedCost"] = None
        groups[strategy] = {
            "accounting": coverage,
            "usage": usage,
            "outcomes": outcomes,
            "states": {
                state: sum(t.status == state for t in selected)
                for state in sorted({t.status for t in selected})
            },
            "reviewPending": sum(t.semantic_review == "PENDING" for t in selected),
            "byCase": {
                identifier: {
                    "repetitions": len(repeated),
                    "successes": sum(t.success and t.status == "COMPLETE" for t in repeated),
                    "successStandardDeviation": pstdev(
                        int(t.success and t.status == "COMPLETE") for t in repeated
                    )
                    if len(repeated) > 1
                    else None,
                    "meanElapsedSeconds": mean(
                        t.elapsed_seconds for t in repeated if t.elapsed_seconds is not None
                    )
                    if any(t.elapsed_seconds is not None for t in repeated)
                    else None,
                }
                for identifier in sorted({t.case_id for t in selected})
                if (repeated := [t for t in selected if t.case_id == identifier])
            },
            "releaseAccepted": False,
        }
    return {
        "boundary": "合成设备状态；真实模型与生产Agent另行标记；未计真实现场解决率",
        "strategies": groups,
    }
