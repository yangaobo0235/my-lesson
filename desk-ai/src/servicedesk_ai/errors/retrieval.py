"""双路检索和索引执行的稳定业务错误。作者：yangaobo。"""

from enum import Enum


class RetrievalErrorCode(Enum):
    """授权检索结果与双副本完整性拒绝。"""

    RETRIEVAL_UNAVAILABLE = (5101, "知识双路检索暂不可用")
    INDEX_UNAVAILABLE = (5102, "必要索引副本暂不可用")

    @property
    def code(self) -> int:
        """稳定编号不决定HTTP状态。"""
        return self.value[0]

    @property
    def message(self) -> str:
        """只包含安全中文提示。"""
        return self.value[1]
