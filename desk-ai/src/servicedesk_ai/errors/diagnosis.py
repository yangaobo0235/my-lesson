"""AI领域实际消费者使用的稳定业务错误，传输状态留在边界。作者：yangaobo。"""

from enum import Enum


class DiagnosisErrorCode(Enum):
    """沿接口约定登记的队列、日额度、双路检索及草稿过期语义。"""

    GENERATION_QUEUE_FULL = (5001, "AI运行队列繁忙，请稍后重试")
    DAILY_GENERATION_LIMIT = (5002, "今日新生成次数已用尽")
    RETRIEVAL_UNAVAILABLE = (5101, "知识双路检索暂不可用")
    INDEX_UNAVAILABLE = (5102, "必要索引副本暂不可用")
    DRAFT_EXPIRED = (5201, "动作草稿已过期，请核对后重新创建")

    @property
    def code(self) -> int:
        """稳定编号不决定HTTP状态。"""
        return self.value[0]

    @property
    def message(self) -> str:
        """只包含安全中文提示。"""
        return self.value[1]
