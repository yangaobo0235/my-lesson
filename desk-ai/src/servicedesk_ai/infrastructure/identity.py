"""跨语言身份客户端；TLS、调用凭据与用户会话失败采用不同错误语义。"""

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from pathlib import Path

import grpc
from google.rpc import status_pb2
from opentelemetry import propagate

from desk_contracts.common.v1.common_pb2 import ActorRef, RpcErrorDetail
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity, VerifyBrowserSessionRequest
from desk_contracts.user.v1.identity_pb2_grpc import IdentityServiceStub
from servicedesk_ai.bootstrap.components import RpcSettings


@dataclass
class IdentityFailure(Exception):
    """只保留安全错误；认证依赖故障不转为用户退出。"""

    code: int
    message: str
    trace_id: str | None = None


def translate_failure(error: grpc.aio.AioRpcError) -> IdentityFailure:
    """仅接受可信RPC状态与登记详情组合，忽略任意上游文本。"""
    permitted = {
        2001: (grpc.StatusCode.UNAUTHENTICATED, "请先登录"),
        2002: (grpc.StatusCode.UNAUTHENTICATED, "会话已失效，请重新登录"),
        2004: (grpc.StatusCode.PERMISSION_DENIED, "请先修改密码"),
        1001: (grpc.StatusCode.INVALID_ARGUMENT, "请求参数不合法"),
        1003: (grpc.StatusCode.PERMISSION_DENIED, "无权执行此操作"),
        1002: (grpc.StatusCode.NOT_FOUND, "请求资源不存在"),
        1004: (grpc.StatusCode.FAILED_PRECONDITION, "当前状态或版本已变化"),
        3001: (grpc.StatusCode.FAILED_PRECONDITION, "客户当前未开通有效支持服务"),
        3101: (grpc.StatusCode.FAILED_PRECONDITION, "设备不可发起该新业务"),
    }
    for key, value in error.trailing_metadata() or ():
        if key != "grpc-status-details-bin" or not isinstance(value, bytes):
            continue
        try:
            status = status_pb2.Status.FromString(value)
            if status.code != error.code().value[0]:
                break
            for packed in status.details:
                detail = RpcErrorDetail()
                if packed.Unpack(detail) and detail.code in permitted:
                    transport, message = permitted[detail.code]
                    if error.code() == transport:
                        return IdentityFailure(detail.code, message, detail.trace_id)
        except Exception:
            break
    if error.code() == grpc.StatusCode.DEADLINE_EXCEEDED:
        return IdentityFailure(1013, "身份依赖响应超时")
    return IdentityFailure(1011, "身份依赖暂不可用")


class IdentityClient:
    """进程内复用stub，所有调用限定父预算与三秒身份Deadline。"""

    def __init__(self, channel: grpc.aio.Channel, token: str) -> None:
        """通道由调用方生命周期持有，凭据不写日志。"""
        self._stub = IdentityServiceStub(channel)
        self._metadata = (("authorization", f"Bearer {token}"),)

    def _traced_metadata(self) -> tuple[tuple[str, str], ...]:
        """从当前OpenTelemetry上下文传播W3C字段，不传播业务正文或凭据标签。"""
        carrier: dict[str, str] = {}
        propagate.inject(carrier)
        return self._metadata + tuple(
            (key, value) for key, value in carrier.items() if key in {"traceparent", "tracestate"}
        )

    async def verify_browser(
        self, cookie: str, method: str, csrf: str | None, *, remaining_seconds: float = 3
    ) -> VerifiedIdentity:
        """原Cookie只传user，后续内部业务使用返回的ActorRef。"""
        request = VerifyBrowserSessionRequest(cookie=cookie, method=method)
        if csrf is not None:
            request.csrf_token = csrf
        if remaining_seconds <= 0:
            raise IdentityFailure(1013, "身份依赖响应超时")
        try:
            return await self._stub.VerifyBrowserSession(
                request,
                timeout=min(2.9, remaining_seconds),
                metadata=self._traced_metadata(),
                wait_for_ready=False,
            )
        except grpc.aio.AioRpcError as error:
            raise translate_failure(error) from None

    async def verify_ref(
        self, actor: ActorRef, *, remaining_seconds: float = 3
    ) -> VerifiedIdentity:
        """后台重核原会话与当前范围，不复制历史角色。"""
        if remaining_seconds <= 0:
            raise IdentityFailure(1013, "身份依赖响应超时")
        try:
            return await self._stub.VerifySessionRef(
                actor,
                timeout=min(2.9, remaining_seconds),
                metadata=self._traced_metadata(),
                wait_for_ready=False,
            )
        except grpc.aio.AioRpcError as error:
            raise translate_failure(error) from None


@asynccontextmanager
async def open_identity(settings: RpcSettings) -> AsyncIterator[IdentityClient]:
    """同一事件循环拥有TLS通道，启动两秒连接预算，关闭最多一秒排空。"""
    if not settings.service_token.get_secret_value() or not all(
        (settings.ca_file, settings.certificate_file, settings.private_key_file)
    ):
        raise ValueError("身份RPC必须配置独立服务凭据及双向TLS文件")
    credentials = grpc.ssl_channel_credentials(
        root_certificates=Path(settings.ca_file).read_bytes(),
        private_key=Path(settings.private_key_file).read_bytes(),
        certificate_chain=Path(settings.certificate_file).read_bytes(),
    )
    channel = grpc.aio.secure_channel(
        settings.user_target,
        credentials,
        options=(
            ("grpc.enable_retries", 0),
            ("grpc.max_receive_message_length", settings.max_message_bytes),
            ("grpc.max_send_message_length", settings.max_message_bytes),
        ),
    )
    try:
        async with asyncio.timeout(2):
            await channel.channel_ready()
        yield IdentityClient(channel, settings.service_token.get_secret_value())
    finally:
        await channel.close(grace=1)
