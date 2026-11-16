"""内部RPC的状态及父剩余预算合同，不创建连接。作者：yangaobo。"""

from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from time import monotonic

import grpc

_DEADLINE: ContextVar[float | None] = ContextVar("rpc_deadline", default=None)


def maximum_seconds(method: str) -> float:
    """身份3秒、领域5秒、索引状态15秒、精确原件流120秒。"""
    return {
        "VerifyBrowserSession": 3.0,
        "VerifySessionRef": 3.0,
        "GetStatus": 15.0,
        "ReadOriginal": 120.0,
    }.get(method.rsplit("/", 1)[-1], 5.0)


def remaining_seconds(maximum: float) -> float:
    """父预算包含本轮全部子调用，耗尽时不重新获得完整超时。"""
    deadline = _DEADLINE.get()
    remaining = min(maximum, deadline - monotonic()) if deadline is not None else maximum
    if remaining <= 0:
        raise TimeoutError("RPC总体预算已耗尽")
    return remaining


@contextmanager
def request_budget(seconds: float) -> Iterator[None]:
    """子作用域只能收紧截止时间，退出后恢复所属任务上下文。"""
    deadline = monotonic() + remaining_seconds(seconds)
    token = _DEADLINE.set(deadline)
    try:
        yield
    finally:
        _DEADLINE.reset(token)


def status_for_code(code: int) -> grpc.StatusCode:
    """逐项转换已登记语义，未知本地错误按INTERNAL处理。"""
    return {
        1001: grpc.StatusCode.INVALID_ARGUMENT,
        1006: grpc.StatusCode.INVALID_ARGUMENT,
        1007: grpc.StatusCode.INVALID_ARGUMENT,
        1008: grpc.StatusCode.INVALID_ARGUMENT,
        1009: grpc.StatusCode.INVALID_ARGUMENT,
        2005: grpc.StatusCode.INVALID_ARGUMENT,
        4002: grpc.StatusCode.INVALID_ARGUMENT,
        4003: grpc.StatusCode.INVALID_ARGUMENT,
        1002: grpc.StatusCode.NOT_FOUND,
        1003: grpc.StatusCode.PERMISSION_DENIED,
        2004: grpc.StatusCode.PERMISSION_DENIED,
        2001: grpc.StatusCode.UNAUTHENTICATED,
        2002: grpc.StatusCode.UNAUTHENTICATED,
        2003: grpc.StatusCode.UNAUTHENTICATED,
        1004: grpc.StatusCode.FAILED_PRECONDITION,
        1005: grpc.StatusCode.FAILED_PRECONDITION,
        2102: grpc.StatusCode.FAILED_PRECONDITION,
        2201: grpc.StatusCode.FAILED_PRECONDITION,
        3001: grpc.StatusCode.FAILED_PRECONDITION,
        3101: grpc.StatusCode.FAILED_PRECONDITION,
        3102: grpc.StatusCode.FAILED_PRECONDITION,
        3201: grpc.StatusCode.FAILED_PRECONDITION,
        3301: grpc.StatusCode.FAILED_PRECONDITION,
        4001: grpc.StatusCode.FAILED_PRECONDITION,
        4201: grpc.StatusCode.FAILED_PRECONDITION,
        5201: grpc.StatusCode.FAILED_PRECONDITION,
        1010: grpc.StatusCode.RESOURCE_EXHAUSTED,
        2006: grpc.StatusCode.RESOURCE_EXHAUSTED,
        5001: grpc.StatusCode.RESOURCE_EXHAUSTED,
        5002: grpc.StatusCode.RESOURCE_EXHAUSTED,
        1011: grpc.StatusCode.UNAVAILABLE,
        5101: grpc.StatusCode.UNAVAILABLE,
        5102: grpc.StatusCode.UNAVAILABLE,
        1013: grpc.StatusCode.DEADLINE_EXCEEDED,
    }.get(code, grpc.StatusCode.INTERNAL)
