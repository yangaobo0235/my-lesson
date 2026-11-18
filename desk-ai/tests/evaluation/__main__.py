"""显式准备、计划与运行评测；pytest不会调用真实模型或修改真实服务。作者：yangaobo。"""

import argparse
import asyncio
import hashlib
import json
import shutil
import time
from collections import Counter
from contextlib import closing
from pathlib import Path

import httpx

from servicedesk_ai.bootstrap.settings import get_settings
from servicedesk_ai.infrastructure.models import ModelProviderError
from tests.evaluation.ablations import VARIANTS
from tests.evaluation.catalog import CASES, FAMILIES
from tests.evaluation.client import ApiFailure, Client, key, write_json
from tests.evaluation.environment import InvalidCheck, Trial, UnsafeAction, summarize
from tests.evaluation.prepare import prepare
from tests.evaluation.strategies import baseline, dynamic


def output_path(value: str) -> Path:
    """所有可再生评测产物必须位于当前真实项目根.local内。"""
    root = Path(__file__).resolve().parents[3] / ".local"
    path = Path(value).resolve()
    if not path.is_relative_to(root.resolve()):
        raise argparse.ArgumentTypeError("评测产物必须位于项目根.local内")
    return path


def parser() -> argparse.ArgumentParser:
    """默认只输出计划，真实写入及付费通过明确子命令启动。"""
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("command", choices=["plan", "prepare", "run"])
    result.add_argument("--base-url", default="http://127.0.0.1:26101")
    result.add_argument("--accounts", type=Path)
    result.add_argument("--bindings", type=output_path)
    result.add_argument("--output", type=output_path)
    result.add_argument("--selection", type=output_path)
    result.add_argument("--ablation", choices=VARIANTS, default="full")
    result.add_argument("--scoped-worker", action="store_true")
    result.add_argument("--family", choices=[family.key for family in FAMILIES])
    result.add_argument("--case", choices=[case.identifier for case in CASES])
    result.add_argument(
        "--split", choices=["development", "validation", "holdout", "all"], default="development"
    )
    result.add_argument(
        "--strategies",
        nargs="+",
        choices=["single_rag", "fixed_procedure", "dynamic"],
        default=["single_rag", "fixed_procedure", "dynamic"],
    )
    result.add_argument("--repetitions", type=int, choices=range(1, 6), default=3)
    return result


def select_trials(arguments: argparse.Namespace, cases: list) -> set[tuple[str, str, int]]:
    """补测显式列出原实例身份，严格限制筛选范围，不复制旧结果伪装新批次。"""
    allowed = {
        (case.identifier, strategy, repeat)
        for case in cases
        for strategy in arguments.strategies
        for repeat in range(1, arguments.repetitions + 1)
    }
    if arguments.selection is None:
        return allowed
    rows = json.loads(arguments.selection.read_text(encoding="utf-8"))
    if not isinstance(rows, list) or not rows:
        raise SystemExit("补测清单必须是非空实例数组")
    selected = set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"caseId", "strategy", "repeat"}:
            raise SystemExit("补测清单字段不符合合同")
        if (
            not isinstance(row["caseId"], str)
            or not isinstance(row["strategy"], str)
            or type(row["repeat"]) is not int
        ):
            raise SystemExit("补测身份类型不合法")
        identity = (row["caseId"], row["strategy"], row["repeat"])
        if identity not in allowed or identity in selected:
            raise SystemExit("补测身份重复或超出本批筛选范围")
        selected.add(identity)
    return selected


def main() -> None:
    """按冻结实例运行，保留无效/失败/未知和实际账目，不自动补跑挑成绩。"""
    arguments = parser().parse_args()
    if arguments.ablation != "full" and arguments.strategies != ["dynamic"]:
        raise SystemExit("消融批次只允许dynamic策略")
    if arguments.ablation != "full" and not arguments.scoped_worker:
        raise SystemExit("消融必须使用指定批次worker，不能由普通worker执行")
    cases = [
        case
        for case in CASES
        if (arguments.split == "all" or case.family.split == arguments.split)
        and (not arguments.family or case.family.key == arguments.family)
        and (not arguments.case or case.identifier == arguments.case)
    ]
    if not cases:
        raise SystemExit("没有符合筛选的实例")
    selected = select_trials(arguments, cases)
    ai_root = Path(__file__).resolve().parents[2]
    frozen_files = sorted(Path(__file__).parent.glob("*.py")) + sorted(
        (ai_root / "src/servicedesk_ai").rglob("*.py")
    )
    source_hashes = {
        str(path.relative_to(ai_root)).replace("\\", "/"): hashlib.sha256(
            path.read_bytes()
        ).hexdigest()
        for path in frozen_files
    }
    plan = {
        "schemaVersion": 1,
        "scoringVersion": "v2-restored-answer-required",
        "boundary": "原创合成设备环境；未经过领域专家审阅，不等同真实现场验收",
        "sources": source_hashes,
        "cases": [case.identifier for case in cases],
        "splitCounts": dict(Counter(case.family.split for case in cases)),
        "strategies": arguments.strategies,
        "repetitions": arguments.repetitions,
        "plannedTrials": len(selected),
        "caseRepetitionCoverage": len(cases) == 60
        and arguments.repetitions >= 3
        and len(selected) == len(cases) * len(arguments.strategies) * arguments.repetitions,
        "ablationsImplemented": True,
        "ablation": arguments.ablation,
        "scopedWorker": arguments.scoped_worker,
        "expertReviewed": False,
    }
    if arguments.selection is not None:
        plan["selection"] = [
            {"caseId": case, "strategy": strategy, "repeat": repeat}
            for case, strategy, repeat in sorted(selected)
        ]
    if arguments.command == "plan":
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return
    if arguments.accounts is None or arguments.bindings is None:
        raise SystemExit("真实命令必须提供--accounts原生凭据文件及--bindings产物路径")
    settings = get_settings()
    if settings.environment != "dev" or settings.resource_env != "test":
        raise SystemExit("拒绝向生产环境安装或运行合成夹具")
    if arguments.command == "prepare":
        prepare(
            arguments.base_url,
            arguments.accounts,
            arguments.bindings,
            [family for family in FAMILIES if any(case.family == family for case in cases)],
        )
        return
    if arguments.output is None:
        raise SystemExit("run必须提供--output批次目录")
    bindings = json.loads(arguments.bindings.read_text(encoding="utf-8"))["families"]
    plan.update(
        model=settings.model.chat_model,
        embedding=settings.model.embedding_model,
        temperature=settings.model.temperature,
        thinking=settings.model.enable_thinking,
        executionBudget=settings.execution.model_dump(),
        taskBudget=settings.task_budget.model_dump(mode="json"),
        bindings={
            family: binding
            for family, binding in bindings.items()
            if any(case.family.key == family for case in cases)
        },
    )
    plan_file = arguments.output / "plan.json"
    if plan_file.exists() and json.loads(plan_file.read_text(encoding="utf-8")) != plan:
        raise SystemExit("批次配置或实现已变化，请使用新批次；不得覆盖旧结果")
    write_json(plan_file, plan)
    for source in frozen_files:
        target = arguments.output / "sources" / source.relative_to(ai_root)
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            shutil.copyfile(source, target)
        elif (
            hashlib.sha256(target.read_bytes()).hexdigest()
            != hashlib.sha256(source.read_bytes()).hexdigest()
        ):
            raise SystemExit("已冻结源码副本摘要失配，拒绝覆盖")
    halted = any(
        Trial.model_validate_json(path.read_text(encoding="utf-8")).status in {"UNKNOWN", "RUNNING"}
        for path in arguments.output.glob("*.json")
        if path.name not in {"plan.json", "summary.json"}
    )
    with closing(Client(arguments.base_url, arguments.accounts, "customer")) as client:
        # 同一批次复用一个真实登录会话，避免逐例登录触发防刷；就绪前不创建试验。
        client.call("ai/conversations?pageSize=1")
        for case in cases:
            if halted:
                break
            binding = bindings.get(case.family.key)
            if not binding or not binding.get("ready"):
                raise SystemExit("该族尚未通过真实索引及发布：" + case.family.key)
            for strategy in arguments.strategies:
                if halted:
                    break
                for repeat in range(1, arguments.repetitions + 1):
                    path = arguments.output / f"{case.identifier}.{strategy}.{repeat}.json"
                    if (case.identifier, strategy, repeat) not in selected:
                        continue
                    if path.exists():
                        # UNKNOWN/RUNNING也不自动重付费；操作人须先沿记录的Run身份核对。
                        continue
                    trial = Trial(
                        case_id=case.identifier,
                        family=case.family.key,
                        split=case.family.split,
                        repeat=repeat,
                        strategy=strategy,
                        expected=case.expected,
                        status="RUNNING",
                    )

                    def save(path: Path = path, trial: Trial = trial) -> None:
                        """每个外部意图及结果落盘，崩溃也不丢失原请求身份。"""
                        write_json(path, trial.model_dump(mode="json"))

                    save()
                    start = time.monotonic()
                    try:
                        if strategy == "dynamic":
                            dynamic(
                                client,
                                case,
                                binding,
                                trial,
                                save,
                                worker_batch=arguments.output if arguments.scoped_worker else None,
                                ablation=arguments.ablation,
                            )
                        else:
                            asyncio.run(baseline(client, case, binding, trial, save))
                        halted = trial.status in {"UNKNOWN", "RUNNING"}
                    except UnsafeAction as error:
                        trial.status, trial.error_type = "UNSAFE", type(error).__name__
                        halted = True
                    except InvalidCheck as error:
                        trial.status, trial.error_type = "INVALID", type(error).__name__
                    except (httpx.TimeoutException, httpx.NetworkError) as error:
                        trial.status, trial.error_type = "UNKNOWN", type(error).__name__
                        halted = True
                    except ModelProviderError as error:
                        trial.status = "FAILED"
                        trial.error_type = "ModelProviderError:" + error.reason
                        halted = True
                    except Exception as error:
                        trial.status = "FAILED"
                        trial.error_type = (
                            f"ApiFailure:{error.code}"
                            if isinstance(error, ApiFailure)
                            else type(error).__name__
                        )
                        if isinstance(error, ApiFailure) and error.code in {
                            1001,
                            1010,
                            1011,
                            1013,
                            2001,
                            2002,
                            2004,
                            2006,
                            5002,
                        }:
                            halted = True
                    finally:
                        trial.elapsed_seconds = round(time.monotonic() - start, 3)
                        if strategy == "dynamic" and trial.status not in {"UNKNOWN", "RUNNING"}:
                            task_ids = [
                                value["taskId"] for value in trial.outputs if "taskId" in value
                            ]
                            if task_ids:
                                try:
                                    task = client.call("ai/tasks/" + task_ids[-1])
                                    if task["status"] in {
                                        "ACTIVE",
                                        "WAITING_USER",
                                        "BLOCKED_EXTERNAL",
                                    }:
                                        client.call(
                                            f"ai/tasks/{task_ids[-1]}/close",
                                            {
                                                "clientActionId": key(),
                                                "taskStateVersion": task["taskStateVersion"],
                                                "reason": (
                                                    "合成评测结束，保留原检查、失败及调用账目。"
                                                ),
                                            },
                                        )
                                except Exception as error:
                                    trial.outputs.append({"cleanupError": type(error).__name__})
                        save()
                    print(
                        case.identifier, strategy, repeat, trial.status, trial.success, flush=True
                    )
                    if halted:
                        break
    trials = [
        Trial.model_validate_json(path.read_text(encoding="utf-8"))
        for path in sorted(arguments.output.glob("*.json"))
        if path.name not in {"plan.json", "summary.json"}
    ]
    report = summarize(trials) | {
        "plannedTrials": plan["plannedTrials"],
        "recordedTrials": len(trials),
        "caseRepetitionCoverage": plan["caseRepetitionCoverage"],
        "haltedByDependencyOrQuota": halted,
        "ablationsImplemented": True,
        "ablation": arguments.ablation,
        "scopedWorker": arguments.scoped_worker,
        "expertReviewed": False,
    }
    write_json(arguments.output / "summary.json", report)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
