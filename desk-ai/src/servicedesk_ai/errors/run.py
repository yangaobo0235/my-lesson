"""有界运行的稳定业务错误；执行诊断字符串独立保存。作者：yangaobo。"""

from enum import Enum


class RunErrorCode(Enum):
    """运行队列、日次数和跨轮累计预算拒绝。"""

    RUN_QUEUE_FULL = (5001, "AI运行队列繁忙，请稍后重试")
    DAILY_RUN_LIMIT = (5002, "今日新运行次数已用尽")
    TASK_BUDGET_EXHAUSTED = (5003, "当前诊断任务的累计预算已用尽")

    @property
    def code(self) -> int:
        """稳定编号不决定HTTP状态。"""
        return self.value[0]

    @property
    def message(self) -> str:
        """只包含安全中文提示。"""
        return self.value[1]
