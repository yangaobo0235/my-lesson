"""在HTTP边界显式映射公共错误，保留框架状态和必要头。"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.draft import DraftErrorCode
from servicedesk_ai.errors.generation import GenerationErrorCode
from servicedesk_ai.errors.retrieval import RetrievalErrorCode
from servicedesk_ai.infrastructure.identity import IdentityFailure
from servicedesk_ai.schemas.result import Result

LOGGER = logging.getLogger(__name__)


def _error_for_status(status: int) -> CommonErrorCode:
    """为框架状态选择安全公共错误，不按业务码计算HTTP状态。"""
    mapping = {
        403: CommonErrorCode.FORBIDDEN,
        404: CommonErrorCode.NOT_FOUND,
        405: CommonErrorCode.METHOD_NOT_ALLOWED,
        409: CommonErrorCode.CONFLICT,
        413: CommonErrorCode.PAYLOAD_TOO_LARGE,
        415: CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
        416: CommonErrorCode.RANGE_NOT_SATISFIABLE,
        429: CommonErrorCode.TOO_MANY_REQUESTS,
        502: CommonErrorCode.UPSTREAM_PROTOCOL_ERROR,
        503: CommonErrorCode.SERVICE_UNAVAILABLE,
        504: CommonErrorCode.DEPENDENCY_TIMEOUT,
    }
    default = (
        CommonErrorCode.INVALID_REQUEST if 400 <= status < 500 else CommonErrorCode.INTERNAL_ERROR
    )
    return mapping.get(status, default)


def _status_for_error(
    error: CommonErrorCode | GenerationErrorCode | RetrievalErrorCode | DraftErrorCode,
) -> int:
    """只为已登记错误显式选择传输状态。"""
    if isinstance(error, (GenerationErrorCode, RetrievalErrorCode, DraftErrorCode)):
        return {
            GenerationErrorCode.GENERATION_QUEUE_FULL: 503,
            GenerationErrorCode.DAILY_GENERATION_LIMIT: 429,
            RetrievalErrorCode.RETRIEVAL_UNAVAILABLE: 503,
            RetrievalErrorCode.INDEX_UNAVAILABLE: 503,
            DraftErrorCode.DRAFT_EXPIRED: 409,
        }[error]
    return {
        CommonErrorCode.INTERNAL_ERROR: 500,
        CommonErrorCode.INVALID_REQUEST: 400,
        CommonErrorCode.NOT_FOUND: 404,
        CommonErrorCode.FORBIDDEN: 403,
        CommonErrorCode.CONFLICT: 409,
        CommonErrorCode.DUPLICATE_VALUE: 409,
        CommonErrorCode.METHOD_NOT_ALLOWED: 405,
        CommonErrorCode.PAYLOAD_TOO_LARGE: 413,
        CommonErrorCode.UNSUPPORTED_MEDIA_TYPE: 415,
        CommonErrorCode.RANGE_NOT_SATISFIABLE: 416,
        CommonErrorCode.TOO_MANY_REQUESTS: 429,
        CommonErrorCode.SERVICE_UNAVAILABLE: 503,
        CommonErrorCode.UPSTREAM_PROTOCOL_ERROR: 502,
        CommonErrorCode.DEPENDENCY_TIMEOUT: 504,
    }[error]


def _response(
    request: Request,
    error: CommonErrorCode | GenerationErrorCode | RetrievalErrorCode | DraftErrorCode,
    status: int,
) -> JSONResponse:
    """只输出安全Result，不转发异常原始消息。"""
    trace_id = request.state.trace_id
    result = Result[None](code=error.code, message=error.message, trace_id=trace_id)
    return JSONResponse(
        result.model_dump(mode="json", by_alias=True),
        status_code=status,
        headers={"X-Trace-Id": trace_id},
    )


def register_error_handlers(app: FastAPI) -> None:
    """向所属应用注册框架、输入、业务与未知错误转换。"""

    @app.exception_handler(IdentityFailure)
    async def identity_error(request: Request, exception: IdentityFailure) -> JSONResponse:
        """只有可信user合同2001/2002为用户失效，服务认证失败保持依赖故障。"""
        statuses = {
            2001: 401,
            2002: 401,
            2004: 403,
            1001: 400,
            1003: 403,
            1002: 404,
            1004: 409,
            3001: 409,
            3101: 409,
            3102: 409,
            1011: 503,
            1012: 502,
            1013: 504,
        }
        code = exception.code if exception.code in statuses else 1011
        result = Result[None](
            code=code,
            message=exception.message if code == exception.code else "身份依赖暂不可用",
            trace_id=request.state.trace_id,
        )
        return JSONResponse(
            result.model_dump(mode="json", by_alias=True),
            status_code=statuses[code],
            headers={"X-Trace-Id": request.state.trace_id},
        )

    @app.exception_handler(HTTPException)
    async def framework_error(request: Request, exception: HTTPException) -> JSONResponse:
        """保留已知框架状态及Allow、Retry-After等必要响应头。"""
        response = _response(
            request, _error_for_status(exception.status_code), exception.status_code
        )
        if exception.headers:
            response.headers.update(exception.headers)
        return response

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exception: RequestValidationError) -> JSONResponse:
        """返回422/1001，隐藏包含输入正文的框架详细错误。"""
        return _response(request, CommonErrorCode.INVALID_REQUEST, 422)

    @app.exception_handler(BusinessError)
    async def business_error(request: Request, exception: BusinessError) -> JSONResponse:
        """仅允许已登记公共错误；本地未知类型回退500/1。"""
        error = exception.error
        if not isinstance(
            error, (CommonErrorCode, GenerationErrorCode, RetrievalErrorCode, DraftErrorCode)
        ):
            error = CommonErrorCode.INTERNAL_ERROR
        return _response(request, error, _status_for_error(error))

    @app.exception_handler(Exception)
    async def unexpected_error(request: Request, exception: Exception) -> JSONResponse:
        """记录安全定位信息，不向浏览器回显内部消息。"""
        LOGGER.error(
            "请求处理异常 traceId=%s exceptionType=%s",
            request.state.trace_id,
            type(exception).__name__,
        )
        return _response(request, CommonErrorCode.INTERNAL_ERROR, 500)
