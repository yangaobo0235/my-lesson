"""提供AI HTTP入口共享的身份核验、资源依赖和响应合同。作者：yangaobo。"""

from typing import Annotated

from fastapi import Depends, Path, Query, Request

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.bootstrap.application import ApplicationServices
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.schemas.result import Result

BusinessId = Annotated[str, Path(pattern=r"^[0-9a-f]{32}$")]
PageNumber = Annotated[int, Query(alias="pageNum", ge=1)]
PageSize = Annotated[int, Query(alias="pageSize", ge=1, le=100)]


def services(request: Request) -> ApplicationServices:
    """业务未装配或监听未就绪时明确503，不启动请求内备用任务。"""
    value = getattr(request.app.state, "services", None)
    if value is None:
        raise BusinessError(CommonErrorCode.SERVICE_UNAVAILABLE)
    return value


async def identity(request: Request) -> VerifiedIdentity:
    """原Cookie仅发送user，写请求包含原CSRF，不信任外来身份头。"""
    cookie = request.headers.get("cookie", "")
    if len(cookie) > 8192:
        raise BusinessError(CommonErrorCode.INVALID_REQUEST)
    return await services(request).rpc.identity.verify_browser(
        cookie, request.method, request.headers.get("x-csrf-token")
    )


Actor = Annotated[VerifiedIdentity, Depends(identity)]


def success[T](request: Request, value: T) -> Result[T]:
    """普通JSON统一成功0并保留当前追踪号。"""
    return Result[T](code=0, message="成功", data=value, trace_id=request.state.trace_id)
