"""诊断生成的稳定业务错误；执行诊断字符串独立保存。作者：yangaobo。"""

from enum import Enum


class GenerationErrorCode(Enum):
    """生成队列及用户日额度拒绝。"""

    GENERATION_QUEUE_FULL = (5001, "AI运行队列繁忙，请稍后重试")
    DAILY_GENERATION_LIMIT = (5002, "今日新生成次数已用尽")

    @property
    def code(self) -> int:
        """稳定编号不决定HTTP状态。"""
        return self.value[0]

    @property
    def message(self) -> str:
        """只包含安全中文提示。"""
        return self.value[1]
