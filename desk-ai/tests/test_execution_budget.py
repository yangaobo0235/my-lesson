"""执行预算与跨轮任务预算的拒绝边界，不调用真实供应商。作者：yangaobo。"""

import asyncio
from contextlib import asynccontextmanager
from decimal import Decimal
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock

import grpc
import pytest

from servicedesk_ai.diagnosis.budget import ExecutionBudgetExceeded
from servicedesk_ai.diagnosis.execution import RunWorker
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.infrastructure.identity import IdentityFailure
from servicedesk_ai.infrastructure.models import (
    ModelMessage,
    ModelProtocolError,
    ModelProviderError,
    ModelRequest,
    ModelResponse,
    ModelToolCall,
    ModelUsage,
)
from servicedesk_ai.repositories import runs
from servicedesk_ai.repositories.call_usage import CallUsage, require_budget
from servicedesk_ai.schemas.model_calls import ModelInputManifest, PricingSnapshot
from servicedesk_ai.schemas.tasks import TaskBudget
from servicedesk_ai.services.indexing import IndexWorker
from servicedesk_ai.services.model_calls import ModelCalls
from servicedesk_ai.services.run_tools import RunTools


@pytest.mark.asyncio
@pytest.mark.parametrize("input_limit,output_limit", [(1, 100), (1000, 1)])
async def test_request_limit_rejects_before_reservation_and_dispatch(input_limit, output_limit):
    """单次输入或输出超限不申请持久调用，也不误报跨轮额度不足。"""
    model = SimpleNamespace(settings=SimpleNamespace(max_output_tokens=100), chat=AsyncMock())
    calls = ModelCalls(None, model, PricingSnapshot())
    calls.reserve = AsyncMock()
    run = SimpleNamespace(
        pipeline_config=SimpleNamespace(
            max_input_tokens=input_limit, max_output_tokens=output_limit
        )
    )
    with pytest.raises(ExecutionBudgetExceeded):
        await calls.prepare(
            run, ModelRequest(messages=(ModelMessage(role="user", content="问题"),)), []
        )
    calls.reserve.assert_not_awaited()
    model.chat.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize("rejected", [True, False])
async def test_provider_failure_preserves_execution_fence_and_unknown_metering(rejected):
    """供应商拒绝落明确失败，传输未知走原UNKNOWN路径，均不写伪造零用量。"""
    db = SimpleNamespace(execute=AsyncMock())

    @asynccontextmanager
    async def begin():
        yield db

    calls = ModelCalls(SimpleNamespace(begin=begin), None, PricingSnapshot())
    calls.own = AsyncMock()
    calls.unknown = AsyncMock()
    run = SimpleNamespace(id="parent", claim_token="original-lease")
    await calls.provider_failure(
        run, "call", ModelProviderError("ACCOUNT_UNAVAILABLE", rejected=rejected)
    )
    if rejected:
        calls.own.assert_awaited_once_with(db, run)
        calls.unknown.assert_not_awaited()
        statement, parameters = db.execute.call_args.args
        assert "status='FAILED'" in str(statement) and "status='IN_FLIGHT'" in str(statement)
        assert "claim_token=:token" in str(statement)
        assert parameters["token"] == run.claim_token
        assert "input_tokens" not in str(statement) and "estimated_cost" not in str(statement)
    else:
        calls.unknown.assert_awaited_once_with(run, "call")
        db.execute.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "current",
    [
        CallUsage(2, 0, Decimal(0), False),
        CallUsage(0, 950, Decimal(0), False),
        CallUsage(0, 0, Decimal(0), False, unsettled=True),
    ],
)
async def test_run_ledger_budget_rejects_before_creating_new_intent(monkeypatch, current):
    """次数、累计Token及未结预留分别阻止新付费意图，不写入新调用。"""
    db = SimpleNamespace(execute=AsyncMock())

    @asynccontextmanager
    async def begin():
        yield db

    calls = ModelCalls(SimpleNamespace(begin=begin), None, PricingSnapshot())
    calls.own = AsyncMock()
    run = Mock(spec=runs.RunLease, id="a" * 32, task_id=None)
    run.pipeline_config = SimpleNamespace(max_model_calls=2, total_tokens=1000)
    monkeypatch.setattr(
        "servicedesk_ai.services.model_calls.usage", AsyncMock(return_value=current)
    )
    manifest = ModelInputManifest(
        request_sha256="a" * 64,
        estimator_version="test",
        input_tokens_upper=100,
        output_tokens_limit=0,
        source_usage_ids=[],
    )
    with pytest.raises(ExecutionBudgetExceeded):
        await calls.reserve(run, "CHAT", "test", manifest, 100, PricingSnapshot())
    db.execute.assert_not_awaited()


def test_task_accumulated_budget_retains_public_business_error():
    """跨轮累计拒绝仍为5003，不能因修复单轮分类改变原公开合同。"""
    policy = TaskBudget(max_model_calls=10, max_tokens=10000, currency="CNY", policy_version="test")
    with pytest.raises(BusinessError) as failure:
        require_budget(CallUsage(policy.max_model_calls, 0, Decimal(0), False), policy)
    assert failure.value.error == RunErrorCode.TASK_BUDGET_EXHAUSTED


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "error,code",
    [
        (ExecutionBudgetExceeded("本轮预算不足"), "EXECUTION_BUDGET_EXCEEDED"),
        (TimeoutError(), "EXECUTION_BUDGET_EXCEEDED"),
        (BusinessError(RunErrorCode.TASK_BUDGET_EXHAUSTED), "TASK_BUDGET_EXHAUSTED"),
        (BusinessError(CommonErrorCode.CONFLICT), "CONTEXT_CHANGED"),
        (ValueError("上下文组重复"), "RUN_EXECUTION_FAILED"),
        (ModelProviderError("ACCOUNT_UNAVAILABLE", rejected=True), "MODEL_PROVIDER_UNAVAILABLE"),
        (IdentityFailure(1011, "依赖不可用"), "DEPENDENCY_UNAVAILABLE"),
        (IdentityFailure(1012, "依赖协议异常"), "DEPENDENCY_UNAVAILABLE"),
        (IdentityFailure(1013, "依赖超时"), "DEPENDENCY_UNAVAILABLE"),
        (IdentityFailure(2002, "会话已失效"), "RUN_EXECUTION_FAILED"),
        (grpc.aio.AioRpcError(grpc.StatusCode.UNAVAILABLE, (), ()), "DEPENDENCY_UNAVAILABLE"),
    ],
)
async def test_worker_persists_correct_scope_and_releases_heartbeat(monkeypatch, error, code):
    """真实worker边界持久化不同拒绝原因；代码错误不能伪报预算或环境变化。"""
    db = object()

    @asynccontextmanager
    async def begin():
        yield db

    run = SimpleNamespace(id="a" * 32, task_id=None, claim_token="lease")
    monkeypatch.setattr(runs, "claim", AsyncMock(return_value=run))
    stopped = AsyncMock()
    monkeypatch.setattr(runs, "stop", stopped)
    worker = RunWorker(SimpleNamespace(begin=begin), None, None, None, None, None, None)
    released = asyncio.Event()

    async def heartbeat(_):
        try:
            await asyncio.Event().wait()
        finally:
            released.set()

    worker.heartbeat = heartbeat
    worker.execute = AsyncMock(side_effect=error)
    assert await worker.run_one()
    stopped.assert_awaited_once_with(db, run.id, "FAILED", code, token="lease")
    assert released.is_set()


@pytest.mark.asyncio
async def test_tool_limit_stops_before_new_intent_or_external_read(monkeypatch):
    """工具次数耗尽只读取原计数，不新增工具意图或执行读取。"""
    db = SimpleNamespace(execute=AsyncMock(return_value=SimpleNamespace(one=lambda: (4, 4))))

    @asynccontextmanager
    async def begin():
        yield db

    tool = RunTools(SimpleNamespace(begin=begin), None, None, None, None)
    tool.definitions = lambda _: [SimpleNamespace(name="read_evidence")]
    tool.read = AsyncMock()
    monkeypatch.setattr(runs, "owned", AsyncMock())
    run = SimpleNamespace(
        id="a" * 32, diagnosis_context_version=0, pipeline_config=SimpleNamespace(max_tool_calls=4)
    )
    call = ModelToolCall(id="call", name="read_evidence", arguments={"chunkId": "b" * 32})
    with pytest.raises(ExecutionBudgetExceeded):
        await tool.execute(run, "model", call)
    assert db.execute.await_count == 1
    tool.read.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "error,code",
    [
        (ExecutionBudgetExceeded("索引预算不足"), "INDEX_BUDGET_EXCEEDED"),
        (ConnectionError(), "INDEX_DEPENDENCY_FAILED"),
    ],
)
async def test_index_budget_is_distinct_from_dependency_failure(monkeypatch, error, code):
    """索引执行预算不足不写成诊断任务5003，也不冒充供应商不可用。"""
    from servicedesk_ai.services import indexing

    db = object()

    @asynccontextmanager
    async def begin():
        yield db

    job = SimpleNamespace(
        id="a" * 32,
        status="RUNNING",
        claim_token="lease",
        processing_config=SimpleNamespace(total_seconds=60),
    )
    monkeypatch.setattr(indexing.jobs, "get_job", AsyncMock(return_value=job))
    finished = AsyncMock()
    monkeypatch.setattr(indexing.jobs, "finish_job", finished)
    worker = IndexWorker(SimpleNamespace(begin=begin), None, None, None, None)

    async def heartbeat(_):
        await asyncio.Event().wait()

    worker.heartbeat = heartbeat
    worker.execute = AsyncMock(side_effect=error)
    assert await worker.run_claimed(job)
    finished.assert_awaited_once_with(db, job, code)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "metering,total,exceeded",
    [
        (ModelUsage(input_tokens=80, output_tokens=30), 110, True),
        (ModelUsage(input_tokens=20, output_tokens=10), 30, False),
        (ModelUsage(input_tokens=20), 90, False),
        (None, 90, False),
    ],
)
@pytest.mark.parametrize("protocol_error", [False, True])
async def test_actual_usage_commits_before_overrun_and_unknown_is_not_zero(
    monkeypatch, metering, total, exceeded, protocol_error
):
    """使用实际账目用例验证结算顺序；替身事务不作为真实MySQL持久化证据。"""
    events = []
    db = SimpleNamespace(
        execute=AsyncMock(
            return_value=SimpleNamespace(
                scalar_one_or_none=lambda: PricingSnapshot().model_dump_json()
            )
        )
    )

    @asynccontextmanager
    async def begin():
        yield db
        events.append("committed")

    @asynccontextmanager
    async def connect():
        assert events == ["committed"]
        yield db

    calls = ModelCalls(SimpleNamespace(begin=begin, connect=connect), None, PricingSnapshot())
    calls.own = AsyncMock()
    run = Mock(spec=runs.RunLease, id="a" * 32, task_id=None, claim_token="lease")
    run.pipeline_config = SimpleNamespace(total_tokens=100)
    monkeypatch.setattr(
        "servicedesk_ai.services.model_calls.usage",
        AsyncMock(return_value=CallUsage(1, total, Decimal(0), True)),
    )
    response = (
        ModelProtocolError("模型提出未授权工具", metering)
        if protocol_error
        else ModelResponse(
            message=ModelMessage(role="assistant", content="公开回答"),
            usage=metering,
            finish_reason="stop",
        )
    )
    if exceeded:
        with pytest.raises(ExecutionBudgetExceeded):
            await calls.complete(run, "b" * 32, response)
    else:
        await calls.complete(run, "b" * 32, response)
    saved = db.execute.call_args.args[1]
    assert events == ["committed"]
    assert saved["status"] == ("FAILED" if protocol_error else "SUCCEEDED")
    if protocol_error:
        import json

        assert json.loads(saved["payload"]) == {
            "schema_version": 1,
            "protocol_error": "模型提出未授权工具",
        }
    assert saved["inputs"] == (metering.input_tokens if metering else None)
    assert saved["outputs"] == (metering.output_tokens if metering else None)
    assert saved["cost"] is None
