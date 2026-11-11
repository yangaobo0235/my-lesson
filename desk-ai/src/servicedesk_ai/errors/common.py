"""维护与Java公共合同同义的数字错误，不存储传输状态。"""

from enum import Enum


class CommonErrorCode(Enum):
    """定义已在接口约定登记的公共错误编号与安全中文说明。"""

    INTERNAL_ERROR = (1, "系统暂时无法完成请求")
    INVALID_REQUEST = (1001, "请求参数不合法")
    NOT_FOUND = (1002, "资源不存在或不可见")
    FORBIDDEN = (1003, "当前操作无权限")
    CONFLICT = (1004, "资源状态或版本已变化")
    DUPLICATE_VALUE = (1005, "业务值已存在")
    METHOD_NOT_ALLOWED = (1006, "请求方法不支持")
    PAYLOAD_TOO_LARGE = (1007, "请求内容超过限制")
    UNSUPPORTED_MEDIA_TYPE = (1008, "请求内容类型不支持")
    RANGE_NOT_SATISFIABLE = (1009, "请求范围不可满足")
    TOO_MANY_REQUESTS = (1010, "请求过于频繁，请稍后重试")
    SERVICE_UNAVAILABLE = (1011, "服务暂时不可用")
    UPSTREAM_PROTOCOL_ERROR = (1012, "依赖服务响应不符合约定")
    DEPENDENCY_TIMEOUT = (1013, "依赖服务响应超时")

    @property
    def code(self) -> int:
        """返回稳定数字错误码。"""
        return self.value[0]

    @property
    def message(self) -> str:
        """返回不含内部数据的安全提示。"""
        return self.value[1]


class BusinessError(Exception):
    """在用例与适配层之间表达安全业务拒绝。"""

    def __init__(self, error: CommonErrorCode) -> None:
        """保留错误定义，不接收任意供应商原始消息。"""
        super().__init__(error.message)
        self.error = error
