"""仅供显式评测进程使用的能力消融；不进入运行制品或公开配置。作者：yangaobo。"""

from contextlib import ExitStack, contextmanager
from dataclasses import replace
from unittest.mock import patch

from servicedesk_ai.diagnosis import execution
from servicedesk_ai.services.run_tools import RunTools

VARIANTS = (
    "full",
    "no_structured_state",
    "no_full_evidence",
    "no_on_demand_context",
    "no_duplicate_detection",
)


def excluded_tools(variant: str) -> set[str]:
    """关闭能力的所有模型入口，保留授权、完整性校验和执行预算。"""
    if variant == "no_full_evidence":
        return {"read_evidence", "load_procedure"}
    if variant in {"no_structured_state", "no_on_demand_context"}:
        return {"read_task_context"}
    return set()


@contextmanager
def ablate(variant: str):
    """在独立测试worker进程内移除单项能力，退出后恢复原方法。

    状态消融去掉事实、检查及假设的初始模型投影与补读入口，保留原聊天反馈；
    完整证据消融只能读候选，宿主仍禁止用预览指导操作；上下文消融仅去掉
    按需补读；重复消融关闭重复短路与无进展停止，累计调用预算始终有效。
    """
    if variant not in VARIANTS:
        raise ValueError("未知消融变体")
    original_definitions = RunTools.definitions
    original_prepare = RunTools.prepare
    original_fit = execution.fit_context

    def definitions(self, run):
        return [
            tool
            for tool in original_definitions(self, run)
            if tool.name not in excluded_tools(variant)
        ]

    def fit_context(template, groups, limit):
        if variant == "no_structured_state":
            groups = [
                group
                for group in groups
                if not group.key.startswith(("fact:", "check:")) and group.key != "hypotheses"
            ]
        return original_fit(template, groups, limit)

    async def prepare(self, run, model_call_id, call):
        prepared = await original_prepare(self, run, model_call_id, call)
        return replace(prepared, repeated_number=None)

    async def no_progress(self, run):
        return False

    with ExitStack() as stack:
        stack.enter_context(patch.object(RunTools, "definitions", definitions))
        if variant == "no_structured_state":
            stack.enter_context(patch.object(execution, "fit_context", fit_context))
            # 初始投影不再包含检查，不能把其原始反馈聊天误当作冗余而删除。
            stack.enter_context(
                patch.object(execution, "represented_feedback", lambda *args: set())
            )
        if variant == "no_duplicate_detection":
            stack.enter_context(patch.object(RunTools, "prepare", prepare))
            stack.enter_context(patch.object(RunTools, "no_progress", no_progress))
        yield
