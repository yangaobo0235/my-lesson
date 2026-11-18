"""用真实本机gRPC监听验证预算、取消与安全错误，不调用外部依赖。作者：yangaobo。"""

import asyncio
from unittest.mock import AsyncMock

import grpc
import pytest
from grpc_status import rpc_status
from opentelemetry.trace import NoOpTracer

from desk_contracts.common.v1.common_pb2 import ActorRef, RpcErrorDetail
from servicedesk_ai.bootstrap.components import RpcSettings
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.identity import IdentityClient, translate_failure
from servicedesk_ai.infrastructure.rpc_contract import (
    maximum_seconds,
    remaining_seconds,
    request_budget,
    status_for_code,
)
from servicedesk_ai.rpc.server import RpcSecurity, failure


@pytest.mark.parametrize(
    "method,seconds",
    [
        ("VerifySessionRef", 3),
        ("CheckContext", 5),
        ("GetStatus", 15),
        ("ReadOriginal", 120),
    ],
)
def test_method_budgets_and_nested_parent(method, seconds):
    """不同方法上限不再混用三秒；父用例只会收紧子调用。"""
    assert maximum_seconds("/service/" + method) == seconds
    with request_budget(0.5):
        assert 0 < remaining_seconds(seconds) <= 0.5
        with request_budget(0.1):
            assert 0 < remaining_seconds(seconds) <= 0.100001
        assert 0.1 < remaining_seconds(seconds) <= 0.5
    assert remaining_seconds(seconds) == seconds


def test_domain_options_keep_delivery_margin_and_never_extend_parent(monkeypatch):
    """原件和领域传输不能贴着方法上限发送；不足余量时不发起新RPC。"""
    client = DomainRpc.__new__(DomainRpc)
    client.token = "test-token"
    clock = [10.0]
    monkeypatch.setattr("servicedesk_ai.infrastructure.rpc_contract.monotonic", lambda: clock[0])
    assert client.options(120)["timeout"] == pytest.approx(119.9)
    assert client.options()["timeout"] == pytest.approx(4.9)
    with request_budget(0.5):
        assert client.options(120)["timeout"] == pytest.approx(0.4)
        clock[0] += 0.45
        with pytest.raises(TimeoutError):
            client.options(120)


@pytest.mark.parametrize(
    "code,expected",
    [
        (1, "INTERNAL"),
        (1012, "INTERNAL"),
        (99999, "INTERNAL"),
        (1001, "INVALID_ARGUMENT"),
        (4002, "INVALID_ARGUMENT"),
        (1002, "NOT_FOUND"),
        (1003, "PERMISSION_DENIED"),
        (2004, "PERMISSION_DENIED"),
        (2001, "UNAUTHENTICATED"),
        (2002, "UNAUTHENTICATED"),
        (3301, "FAILED_PRECONDITION"),
        (4201, "FAILED_PRECONDITION"),
        (5201, "FAILED_PRECONDITION"),
        (5001, "RESOURCE_EXHAUSTED"),
        (5002, "RESOURCE_EXHAUSTED"),
        (5101, "UNAVAILABLE"),
        (1013, "DEADLINE_EXCEEDED"),
    ],
)
def test_registered_transport_semantics(code, expected):
    """系统故障、业务拒绝与资源忙保持不同传输语义。"""
    assert status_for_code(code).name == expected


@pytest.mark.asyncio
async def test_exhausted_parent_does_not_call_identity(monkeypatch):
    """父预算耗尽后不能为身份调用重新获得三秒。"""
    clock = [10.0]
    monkeypatch.setattr("servicedesk_ai.infrastructure.rpc_contract.monotonic", lambda: clock[0])
    client = object.__new__(IdentityClient)
    client._stub = AsyncMock()
    client._metadata = ()
    with request_budget(1):
        clock[0] = 12
        with pytest.raises(TimeoutError):
            await client.verify_ref(ActorRef())
    client._stub.VerifySessionRef.assert_not_called()


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "method,budget",
    [
        ("VerifySessionRef", 3),
        ("CheckContext", 5),
        ("GetStatus", 15),
        ("ReadOriginal", 120),
    ],
)
async def test_actual_server_enforces_method_deadline_and_cancels_slow_handler(method, budget):
    """真实慢响应验证取消传播，不等待完整的120秒或调用收费模型。"""
    token = "k" * 32
    settings = RpcSettings(knowledge_token=token, ticket_token="t" * 32)
    security = RpcSecurity(settings, NoOpTracer())
    path = "/test.Budget/" + method
    security.methods = {path: token}
    cancelled = asyncio.Event()

    async def call(request, context):
        if request == b"slow":
            try:
                await asyncio.Event().wait()
            finally:
                cancelled.set()
        if request == b"fail":
            await failure(context, ValueError("secret-internal-message"))
        return b"ok"

    server = grpc.aio.server(interceptors=[security])
    server.add_generic_rpc_handlers(
        [
            grpc.method_handlers_generic_handler(
                "test.Budget",
                {method: grpc.unary_unary_rpc_method_handler(call)},
            )
        ]
    )
    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()
    try:
        async with grpc.aio.insecure_channel(f"127.0.0.1:{port}") as channel:
            invoke = channel.unary_unary(path)
            metadata = (("authorization", "Bearer " + token),)
            client = DomainRpc.__new__(DomainRpc)
            client.token = token
            assert await invoke(b"ok", **client.options(budget)) == b"ok"
            with pytest.raises(grpc.aio.AioRpcError) as invalid:
                await invoke(b"ok", metadata=metadata, timeout=budget + 1)
            assert invalid.value.code() == grpc.StatusCode.INVALID_ARGUMENT
            with pytest.raises(grpc.aio.AioRpcError) as failed:
                await invoke(b"fail", metadata=metadata, timeout=1)
            assert failed.value.code() == grpc.StatusCode.INTERNAL
            detail = RpcErrorDetail()
            assert rpc_status.from_call(failed.value).details[0].Unpack(detail)
            assert detail.code == 1 and len(detail.trace_id) == 32
            assert "secret" not in detail.message
            assert translate_failure(failed.value).code == 1011
            with pytest.raises(grpc.aio.AioRpcError) as slow:
                await invoke(b"slow", metadata=metadata, timeout=0.05)
            assert slow.value.code() == grpc.StatusCode.DEADLINE_EXCEEDED
            await asyncio.wait_for(cancelled.wait(), timeout=1)
    finally:
        await server.stop(grace=0)
