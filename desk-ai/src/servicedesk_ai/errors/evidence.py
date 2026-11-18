"""知识所有者风险撤回错误的跨语言同义镜像，不自行推断撤权。作者：yangaobo。"""

from enum import Enum


class EvidenceErrorCode(Enum):
    """已有读取权限的来源风险撤回，与1003访问撤权区分。"""

    SOURCE_WITHDRAWN = (5103, "来源已因风险撤回，请重新取得有效依据")

    @property
    def code(self) -> int:
        """返回正式登记的编号。"""
        return self.value[0]

    @property
    def message(self) -> str:
        """返回安全说明。"""
        return self.value[1]
