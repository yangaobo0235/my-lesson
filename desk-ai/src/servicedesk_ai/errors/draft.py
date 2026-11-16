"""动作草稿的稳定业务错误；过期拒绝由草稿用例表达。作者：yangaobo。"""

from enum import Enum


class DraftErrorCode(Enum):
    """草稿采用前的有效期拒绝。"""

    DRAFT_EXPIRED = (5201, "动作草稿已过期，请核对后重新创建")

    @property
    def code(self) -> int:
        """稳定编号不决定HTTP状态。"""
        return self.value[0]

    @property
    def message(self) -> str:
        """只包含安全中文提示。"""
        return self.value[1]
