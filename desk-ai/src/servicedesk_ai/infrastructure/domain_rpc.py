"""跨语言领域客户端及原件流完整性验证，所有通道复用所属进程生命周期。作者：yangaobo。"""

import asyncio
import hashlib
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path

import grpc
from opentelemetry import propagate

from desk_contracts.knowledge.v1.source_pb2 import (
    AuthorizedSources,
    KnowledgeSource,
    OriginalRequest,
    SourceQuery,
    VerifySourcesRequest,
)
from desk_contracts.knowledge.v1.source_pb2_grpc import (
    KnowledgeSourceServiceStub,
    OriginalFileServiceStub,
)
from desk_contracts.ticket.v1.support_pb2_grpc import SupportAccessServiceStub
from servicedesk_ai.bootstrap.components import RpcSettings
from servicedesk_ai.infrastructure.identity import IdentityClient


class DomainRpc:
    """身份和领域授权分别复核，服务凭据不能取代用户范围。"""

    def __init__(
        self,
        identity: IdentityClient,
        ticket: grpc.aio.Channel,
        knowledge: grpc.aio.Channel,
        token: str,
    ) -> None:
        """由装配器创建，客户端不自行决定任意目标地址。"""
        self.identity = identity
        self.ticket = SupportAccessServiceStub(ticket)
        self.knowledge = KnowledgeSourceServiceStub(knowledge)
        self.original = OriginalFileServiceStub(knowledge)
        self.token = token

    def options(self, seconds: float = 3) -> dict[str, object]:
        """只传播受控W3C上下文，业务调用禁自动重试。"""
        carrier: dict[str, str] = {}
        propagate.inject(carrier)
        return {
            "metadata": (("authorization", f"Bearer {self.token}"),)
            + tuple(
                (key, value)
                for key, value in carrier.items()
                if key in {"traceparent", "tracestate"}
            ),
            "timeout": seconds,
            "wait_for_ready": False,
        }

    async def read_original(
        self, document_id: str, job_id: str, expected_sha: str, content_format: str
    ) -> bytes:
        """同一30秒预算内至多重读一次不可用流，始终沿精确原件和原任务身份。"""
        deadline = asyncio.get_running_loop().time() + 30
        for attempt in range(2):
            try:
                return await self._read_original_once(
                    document_id,
                    job_id,
                    expected_sha,
                    content_format,
                    max(0.001, deadline - asyncio.get_running_loop().time()),
                )
            except grpc.aio.AioRpcError as error:
                if (
                    attempt
                    or error.code() != grpc.StatusCode.UNAVAILABLE
                    or asyncio.get_running_loop().time() >= deadline
                ):
                    raise
        raise RuntimeError("原件重读预算已结束")

    async def _read_original_once(
        self,
        document_id: str,
        job_id: str,
        expected_sha: str,
        content_format: str,
        remaining_seconds: float,
    ) -> bytes:
        """先元信息，再连续字节块；最终RPC成功、总量和SHA一致才返回原件。"""
        stream = self.original.ReadOriginal(
            OriginalRequest(document_id=document_id, job_id=job_id),
            **self.options(remaining_seconds),
        )
        data = bytearray()
        total = None
        sha = None
        try:
            async for part in stream:
                if total is None:
                    if (
                        not part.HasField("total_bytes")
                        or not part.HasField("sha256")
                        or not part.HasField("content_format")
                    ):
                        raise ValueError("原件流缺少元信息")
                    total, sha = part.total_bytes, part.sha256
                    limit = 1048576 if content_format == "MARKDOWN" else 20971520
                    if (
                        not 0 < total <= limit
                        or sha != expected_sha
                        or part.content_format != content_format
                    ):
                        raise ValueError("原件元信息不匹配")
                elif (
                    part.HasField("total_bytes")
                    or part.HasField("sha256")
                    or part.HasField("content_format")
                ):
                    raise ValueError("原件元信息重复")
                if part.offset != len(data) or len(part.data) > 65536:
                    raise ValueError("原件流不连续")
                data.extend(part.data)
                if len(data) > total:
                    raise ValueError("原件流超总量")
            if total is None or len(data) != total or hashlib.sha256(data).hexdigest() != sha:
                raise ValueError("原件流未完整核验")
            return bytes(data)
        finally:
            stream.cancel()

    async def sources(self, query: SourceQuery) -> AuthorizedSources:
        """只有knowledge当前授权的任务集合可用于召回过滤。"""
        return await self.knowledge.QueryAuthorizedSources(query, **self.options())

    async def verify_sources(
        self, query: SourceQuery, sources: list[KnowledgeSource]
    ) -> AuthorizedSources:
        """交付和读取重新核验全部实际依赖，不能仅验证最终引用。"""
        return await self.knowledge.VerifySources(
            VerifySourcesRequest(context=query, sources=sources), **self.options()
        )


@asynccontextmanager
async def open_domain_rpc(settings: RpcSettings) -> AsyncIterator[DomainRpc]:
    """在当前事件循环创建三个TLS通道；任一关键监听不可连则不报告就绪。"""
    if not settings.service_token.get_secret_value() or not all(
        (settings.ca_file, settings.certificate_file, settings.private_key_file)
    ):
        raise ValueError("领域RPC缺少服务凭据或双向TLS")
    credentials = grpc.ssl_channel_credentials(
        root_certificates=Path(settings.ca_file).read_bytes(),
        private_key=Path(settings.private_key_file).read_bytes(),
        certificate_chain=Path(settings.certificate_file).read_bytes(),
    )
    channels = []
    try:
        for target in (settings.user_target, settings.ticket_target, settings.knowledge_target):
            channel = grpc.aio.secure_channel(
                target,
                credentials,
                options=(
                    ("grpc.enable_retries", 0),
                    ("grpc.max_receive_message_length", settings.max_message_bytes),
                ),
            )
            channels.append(channel)
        yield DomainRpc(
            IdentityClient(channels[0], settings.service_token.get_secret_value()),
            channels[1],
            channels[2],
            settings.service_token.get_secret_value(),
        )
    finally:
        for channel in reversed(channels):
            await channel.close(grace=1)
