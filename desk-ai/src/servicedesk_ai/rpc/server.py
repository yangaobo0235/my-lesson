"""API同事件循环的双向TLS内部监听，只开放已登记只读方法。作者：yangaobo。"""

from __future__ import annotations

import secrets
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from pathlib import Path
from typing import NoReturn, cast
from uuid import uuid4

import grpc
from google.protobuf import any_pb2
from google.protobuf.message import Message
from google.rpc import status_pb2
from grpc_status import rpc_status
from opentelemetry import propagate, trace

from desk_contracts.ai.v1 import execution_pb2 as messages
from desk_contracts.ai.v1 import execution_pb2_grpc as stubs
from desk_contracts.common.v1.common_pb2 import RpcErrorDetail
from desk_contracts.knowledge.v1.source_pb2 import SourceQuery
from servicedesk_ai.bootstrap.application import ApplicationServices
from servicedesk_ai.bootstrap.components import RpcSettings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.draft import DraftErrorCode
from servicedesk_ai.errors.evidence import EvidenceErrorCode
from servicedesk_ai.errors.retrieval import RetrievalErrorCode
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.infrastructure.identity import IdentityFailure
from servicedesk_ai.infrastructure.rpc_contract import (
    maximum_seconds,
    request_budget,
    status_for_code,
)
from servicedesk_ai.repositories import runs as store
from servicedesk_ai.repositories.index_jobs import read_chunks
from servicedesk_ai.services.evidence import source_query


class RpcSecurity(grpc.aio.ServerInterceptor):
    """凭据仅授权调用服务与具体方法，用户范围仍由实际用例复核。"""

    def __init__(self, settings: RpcSettings, tracer: trace.Tracer) -> None:
        """入站ticket与knowledge密钥各自独立，不复用AI出站身份密钥。"""
        knowledge = settings.knowledge_token.get_secret_value()
        ticket = settings.ticket_token.get_secret_value()
        if len(knowledge) < 32 or len(ticket) < 32 or knowledge == ticket:
            raise ValueError("AI内部调用方凭据缺失或复用")
        self.methods = {
            "/servicedesk.ai.v1.IndexJobService/GetStatus": knowledge,
            "/servicedesk.ai.v1.EvidenceContextService/ResolveContext": knowledge,
            "/servicedesk.ai.v1.ActionDraftService/ValidateDraft": ticket,
        }
        self.tracer = tracer

    async def intercept_service(
        self,
        continuation: Callable[[grpc.HandlerCallDetails], Awaitable[grpc.RpcMethodHandler | None]],
        details: grpc.HandlerCallDetails,
    ) -> grpc.RpcMethodHandler | None:
        """标准TLS验证之后再做常量时间凭据比较和方法白名单。"""
        handler = await continuation(details)
        headers = {
            key: value for key, value in details.invocation_metadata if isinstance(value, str)
        }
        expected = self.methods.get(details.method)
        if handler is None or handler.unary_unary is None:
            return handler
        # grpc通用handler的存根同时服务同步和aio；在适配边界明确实际异步协议。
        unary = cast(
            Callable[[Message, grpc.aio.ServicerContext], Awaitable[Message]], handler.unary_unary
        )

        async def call(request: Message, context: grpc.aio.ServicerContext) -> Message:
            if expected is None or not secrets.compare_digest(
                headers.get("authorization", ""), "Bearer " + expected
            ):
                await context.abort(grpc.StatusCode.PERMISSION_DENIED, "调用服务未获授权")
            remaining = context.time_remaining()
            if remaining is None or remaining > maximum_seconds(details.method) + 0.01:
                await context.abort(
                    grpc.StatusCode.INVALID_ARGUMENT, "内部调用必须声明合同范围内的Deadline"
                )
            parent = propagate.extract(
                {
                    key: value
                    for key, value in headers.items()
                    if key in {"traceparent", "tracestate"}
                }
            )
            with self.tracer.start_as_current_span(
                "ai.rpc", context=parent, record_exception=False, set_status_on_exception=False
            ):
                with request_budget(remaining):
                    return await unary(request, context)

        return grpc.unary_unary_rpc_method_handler(
            call,
            request_deserializer=handler.request_deserializer,
            response_serializer=handler.response_serializer,
        )


async def failure(context: grpc.aio.ServicerContext, error: Exception) -> NoReturn:
    """标准数字详情不替代传输状态，未知错误不暴露内部文本。"""
    if isinstance(error, BusinessError) and isinstance(
        error.error,
        (CommonErrorCode, RunErrorCode, RetrievalErrorCode, DraftErrorCode, EvidenceErrorCode),
    ):
        code, message = error.error.code, error.error.message
    elif isinstance(error, TimeoutError):
        code, message = 1013, "内部调用超出预算"
    elif isinstance(error, IdentityFailure):
        code, message = error.code, error.message
    else:
        code, message = 1, "内部依赖暂不可用"
    status = status_for_code(code)
    packed = any_pb2.Any()
    packed.Pack(
        RpcErrorDetail(
            code=code,
            message=message,
            trace_id=(
                format(trace.get_current_span().get_span_context().trace_id, "032x")
                if trace.get_current_span().get_span_context().is_valid
                else uuid4().hex
            ),
        )
    )
    await context.abort_with_status(
        rpc_status.to_status(
            status_pb2.Status(code=status.value[0], message=message, details=[packed])
        )
    )


class IndexRpc(stubs.IndexJobServiceServicer):
    """仅查询持久受理状态，成功状态还要检查当前双索引副本。"""

    def __init__(self, services: ApplicationServices) -> None:
        """调用与receiver共用的索引用例。"""
        self.services = services

    async def GetStatus(
        self, request: messages.IndexStatusRequest, context: grpc.aio.ServicerContext
    ) -> messages.IndexStatus:
        """未见只报告NOT_FOUND，不能据此推断Broker没有在途消息。"""
        try:
            job, count = await self.services.index.get_status(request.job_id)
            if job is None:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            verified = False
            if job.status == "SUCCEEDED":
                async with self.services.index.engine.connect() as db:
                    chunks = await read_chunks(db, job.id)
                verified = await self.services.replicas.verify(job.id, job.document_id, chunks)
            result = messages.IndexStatus(
                job_id=job.id,
                request_hash=job.request_hash,
                last_command=job.last_command,
                status=job.status,
                parser_state=job.parser_state,
                chunk_count=count,
                replicas_verified=verified,
            )
            if job.stage:
                result.stage = job.stage
            if job.page_count is not None:
                result.page_count = job.page_count
            return result
        except Exception as error:
            await failure(context, error)


class DraftRpc(stubs.ActionDraftServiceServicer):
    """草稿复核不写工单或保存第二份消费结果。"""

    def __init__(self, services: ApplicationServices) -> None:
        """与HTTP共用草稿及来源规则。"""
        self.services = services

    async def ValidateDraft(
        self, request: messages.ValidateDraftRequest, context: grpc.aio.ServicerContext
    ) -> messages.ValidatedDraft:
        """实际采用必须重新核验当前拥有者、目标、期限、版本和全部来源。"""
        try:
            return await self.services.drafts.validate(request)
        except Exception as error:
            await failure(context, error)


class EvidenceRpc(stubs.EvidenceContextServiceServicer):
    """只核对历史上下文与实际来源关系，不反调knowledge造成递归授权。"""

    def __init__(self, services: ApplicationServices) -> None:
        """依赖本人会话、user身份与ticket当前权限。"""
        self.services = services

    async def ResolveContext(
        self, request: messages.EvidenceContextRequest, context: grpc.aio.ServicerContext
    ) -> SourceQuery:
        """返回最小范围及原引用约束，knowledge再独立核验发布和适用规则。"""
        try:
            actor = await self.services.rpc.identity.verify_ref(request.actor)
            async with self.services.index.engine.connect() as db:
                run = await store.run(db, request.run_id)
            await self.services.conversations.authorized(actor, run.conversation_id)
            items = await self.services.evidence.list_items(run.id)
            item = next((value for value in items if value.id == request.source_usage_id), None)
            if item is None or item.source_type not in {"CHUNK", "PROCEDURE_CHECK"}:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            result = source_query(run, actor.actor)
            source = item.source_version["knowledge_source"]
            if not isinstance(source, dict):
                raise BusinessError(CommonErrorCode.CONFLICT)
            result.referenced_document_id = source["document_id"]
            result.referenced_index_job_id = source["index_job_id"]
            return result
        except Exception as error:
            await failure(context, error)


@asynccontextmanager
async def open_rpc_server(
    services: ApplicationServices, settings: RpcSettings
) -> AsyncIterator[None]:
    """任一监听启动失败阻止API就绪；退出有界排空并收回端口。"""
    credentials = grpc.ssl_server_credentials(
        [
            (
                Path(settings.private_key_file).read_bytes(),
                Path(settings.certificate_file).read_bytes(),
            )
        ],
        root_certificates=Path(settings.ca_file).read_bytes(),
        require_client_auth=True,
    )
    server = grpc.aio.server(
        interceptors=[RpcSecurity(settings, services.tracer)],
        maximum_concurrent_rpcs=32,
        options=[("grpc.max_receive_message_length", settings.max_message_bytes)],
    )
    stubs.add_IndexJobServiceServicer_to_server(IndexRpc(services), server)
    stubs.add_ActionDraftServiceServicer_to_server(DraftRpc(services), server)
    stubs.add_EvidenceContextServiceServicer_to_server(EvidenceRpc(services), server)
    if server.add_secure_port(settings.listen_address, credentials) == 0:
        raise RuntimeError("AI内部TLS监听绑定失败")
    await server.start()
    try:
        yield
    finally:
        await server.stop(grace=3)
