"""私有任务、来源撤权及当前执行权回归；数据库原子提交另作真实MySQL验收。作者：yangaobo。"""

from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import grpc
import pytest
from google.protobuf.any_pb2 import Any
from google.rpc.status_pb2 import Status

from desk_contracts.common.v1.common_pb2 import ActorRef, RpcErrorDetail
from desk_contracts.ticket.v1.support_pb2 import SupportContext
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.diagnosis.execution import RunWorker
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.evidence import EvidenceErrorCode
from servicedesk_ai.infrastructure.identity import IdentityFailure
from servicedesk_ai.repositories import tasks
from servicedesk_ai.schemas.runs import RunContext, RunPipeline, RunResult
from servicedesk_ai.schemas.tasks import TaskEnvironment
from servicedesk_ai.services.evidence import Evidence, EvidenceService
from servicedesk_ai.services.indexing import IndexWorker
from servicedesk_ai.services.task_access import TaskAccess


class Engine:
    """明确隔离数据库传输；不把本替身当真实事务验收。"""

    def __init__(self):
        self.committed = False

    @asynccontextmanager
    async def begin(self):
        yield object()
        self.committed = True

    @asynccontextmanager
    async def connect(self):
        yield object()


def actor(role="CUSTOMER_USER", owner="a" * 32):
    return VerifiedIdentity(actor=ActorRef(owner_id=owner), organization_id="c" * 32, roles=[role])


def run_snapshot():
    return SimpleNamespace(
        id="d" * 32,
        conversation_id="b" * 32,
        task_id="9" * 32,
        owner_id="a" * 32,
        session_ref="session",
        auth_version=1,
        input_state_version=1,
        diagnosis_context_version=2,
        ticket_row_version=None,
        context_manifest=RunContext(
            mode="CUSTOMER_SELF_SERVICE",
            customer_id="c" * 32,
            service_item_id="e" * 32,
            device_id=None,
            product_model_id=None,
            ticket_id=None,
            support_context=TaskEnvironment(field_sources={}),
            messages=[],
        ),
        pipeline_config=RunPipeline(prompt_version="v2", tool_version="v2", model="model"),
    )


@pytest.mark.asyncio
@pytest.mark.parametrize("current", [2, 3])
async def test_current_environment_controls_paid_continuation(monkeypatch, current):
    """工单普通写版本不打断诊断；环境改变在模型外调前拒绝。"""
    run = run_snapshot()
    worker = object.__new__(RunWorker)
    worker.engine = Engine()
    worker.rpc = SimpleNamespace(
        identity=SimpleNamespace(verify_ref=AsyncMock(return_value=actor()))
    )
    worker.access = SimpleNamespace(
        conversation=AsyncMock(return_value=SimpleNamespace(ticket_id="1" * 32)),
        authorize=AsyncMock(
            return_value=(
                None,
                SupportContext(ticket_row_version=99, diagnosis_context_version=current),
            )
        ),
    )
    worker.evidence = SimpleNamespace(verify=AsyncMock())
    monkeypatch.setattr(
        tasks,
        "task",
        AsyncMock(return_value=SimpleNamespace(task_state_version=1, diagnosis_context_version=2)),
    )
    if current == 2:
        await worker.verify(run)
        worker.evidence.verify.assert_awaited_once()
    else:
        with pytest.raises(BusinessError):
            await worker.verify(run)
        worker.evidence.verify.assert_not_awaited()


@pytest.mark.parametrize("change", ["owner", "customer", "role"])
def test_private_conversation_does_not_follow_ticket_permission(change):
    service = object.__new__(TaskAccess)
    identity = actor()
    conversation = SimpleNamespace(
        owner_id=identity.actor.owner_id,
        mode="CUSTOMER_SELF_SERVICE",
        customer_id=identity.organization_id,
    )
    if change == "owner":
        identity = actor("TECHNICIAN", "f" * 32)
    elif change == "customer":
        conversation.customer_id = "f" * 32
    else:
        identity = actor("KNOWLEDGE_ADMIN")
    with pytest.raises(BusinessError):
        service.owner(identity, conversation)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "code,transport,expected",
    [
        (1003, grpc.StatusCode.PERMISSION_DENIED, CommonErrorCode.FORBIDDEN),
        (5103, grpc.StatusCode.FAILED_PRECONDITION, EvidenceErrorCode.SOURCE_WITHDRAWN),
        (None, grpc.StatusCode.PERMISSION_DENIED, None),
    ],
)
async def test_transitive_fact_cannot_bypass_revoked_knowledge(
    monkeypatch, code, transport, expected
):
    """工具观测的知识依赖撤权，不能经事实摘要绕过。"""
    run = run_snapshot()
    detail = Any()
    detail.Pack(RpcErrorDetail(code=code or 0))
    status = Status(code=transport.value[0], details=[detail])
    rpc = SimpleNamespace(
        verify_sources=AsyncMock(
            side_effect=grpc.aio.AioRpcError(
                transport,
                (),
                (("grpc-status-details-bin", status.SerializeToString()),) if code else (),
                "denied",
            )
        )
    )
    service = EvidenceService(Engine(), rpc)
    fact = Evidence("fact-usage", 1, "FACT", "事实", "摘要", {}, {}, fact_id="fact")
    source = Evidence(
        "knowledge-usage",
        2,
        "CHUNK",
        "知识",
        "摘录",
        {},
        {"knowledge_source": {"document_id": "doc", "index_job_id": "job"}},
    )
    parent = run_snapshot()
    parent.id = "parent"
    service.list_items = AsyncMock(side_effect=[[fact], [source]])
    monkeypatch.setattr(
        "servicedesk_ai.services.evidence.queries.fact_dependency",
        AsyncMock(
            return_value={
                "conversation_id": run.conversation_id,
                "task_id": run.task_id,
                "source_type": "TOOL_OBSERVATION",
                "source_run_id": "parent",
            }
        ),
    )
    monkeypatch.setattr("servicedesk_ai.services.evidence.read_run", AsyncMock(return_value=parent))
    if expected is None:
        with pytest.raises(IdentityFailure) as failure:
            await service.verify(run)
        assert failure.value.code == 1011
    else:
        with pytest.raises(BusinessError) as denied:
            await service.verify(run)
        assert denied.value.error == expected
    query, sources = rpc.verify_sources.call_args.args
    assert query.run_id == parent.id and query.source_usage_id == source.id


@pytest.mark.asyncio
async def test_final_commit_failure_is_not_reported_as_success():
    """worker必须等待最终事务成功，不能因模型已返回就结束为成功。"""
    worker = object.__new__(RunWorker)
    worker.verify = AsyncMock()
    worker.rpc = SimpleNamespace(
        identity=SimpleNamespace(verify_ref=AsyncMock(return_value=actor()))
    )
    worker.completion = SimpleNamespace(
        commit=AsyncMock(side_effect=BusinessError(CommonErrorCode.CONFLICT))
    )
    with pytest.raises(BusinessError):
        await worker.finish(run_snapshot(), RunResult(answer_type="CLARIFICATION", text="请补充"))
    worker.completion.commit.assert_awaited_once()


@pytest.mark.asyncio
async def test_unknown_parser_creation_is_committed_without_new_remote_task(monkeypatch):
    """未知标记须先提交再结束用例，不能随抛错回滚成SUBMITTING。"""
    worker = object.__new__(IndexWorker)
    worker.engine, worker.mineru = Engine(), SimpleNamespace(create=AsyncMock())
    job = SimpleNamespace(id="job", parser_state="SUBMITTING")
    monkeypatch.setattr("servicedesk_ai.services.indexing.jobs.owned", AsyncMock())
    monkeypatch.setattr(
        "servicedesk_ai.services.indexing.jobs.get_job", AsyncMock(return_value=job)
    )
    update = AsyncMock()
    monkeypatch.setattr("servicedesk_ai.services.indexing.queries.mark_parser_unknown", update)
    with pytest.raises(ValueError):
        await worker.parse_pdf(job, b"pdf")
    update.assert_awaited_once()
    assert worker.engine.committed
    worker.mineru.create.assert_not_awaited()


@pytest.mark.asyncio
async def test_cyclic_source_chain_is_rejected_before_visited_shortcut(monkeypatch):
    """共享祖先可复核，环形Run依赖不能被已访问集合伪装成验证成功。"""
    current, parent = run_snapshot(), run_snapshot()
    parent.id = "f" * 32
    service = EvidenceService(Engine(), SimpleNamespace())
    first = Evidence("first", 1, "FACT", "事实", "摘要", {}, {}, fact_id="first")
    second = Evidence("second", 1, "FACT", "事实", "摘要", {}, {}, fact_id="second")
    service.list_items = AsyncMock(side_effect=[[first], [second], [first]])
    monkeypatch.setattr(
        "servicedesk_ai.services.evidence.queries.fact_dependency",
        AsyncMock(
            side_effect=[
                {
                    "conversation_id": current.conversation_id,
                    "task_id": current.task_id,
                    "source_type": "TOOL_OBSERVATION",
                    "source_run_id": parent.id,
                },
                {
                    "conversation_id": current.conversation_id,
                    "task_id": current.task_id,
                    "source_type": "TOOL_OBSERVATION",
                    "source_run_id": current.id,
                },
            ]
        ),
    )
    monkeypatch.setattr(
        "servicedesk_ai.services.evidence.read_run", AsyncMock(side_effect=[parent, current])
    )
    with pytest.raises(BusinessError) as error:
        await service.verify(current)
    assert error.value.error == CommonErrorCode.CONFLICT


@pytest.mark.asyncio
@pytest.mark.parametrize("error", [CommonErrorCode.FORBIDDEN, EvidenceErrorCode.SOURCE_WITHDRAWN])
async def test_answer_checks_source_before_opening_write_transaction(monkeypatch, error):
    """页面隐藏不是授权边界，直接提交被撤回指引的答复也不能写入任务。"""
    from servicedesk_ai.schemas.tasks import CheckAnswerInput
    from servicedesk_ai.services.tasks import TaskService

    service = object.__new__(TaskService)
    service.engine = Engine()
    identity = actor()
    original = SimpleNamespace(id="9" * 32)
    conversation = SimpleNamespace(id="b" * 32)
    service.authorized = AsyncMock(return_value=(original, conversation, None))
    service.evidence = SimpleNamespace(verify=AsyncMock(side_effect=BusinessError(error)))
    monkeypatch.setattr(tasks, "action_receipt", AsyncMock(return_value=None))
    monkeypatch.setattr(
        tasks, "check", AsyncMock(return_value=SimpleNamespace(proposed_run_id="d" * 32))
    )
    monkeypatch.setattr(
        "servicedesk_ai.services.tasks.runs.run", AsyncMock(return_value=run_snapshot())
    )
    form = CheckAnswerInput(
        client_action_id="e" * 32, task_state_version="1", answer={"value": "反馈"}
    )
    with pytest.raises(BusinessError) as rejected:
        await service.answer(identity, original.id, "f" * 32, form)
    assert rejected.value.error == error
    assert not service.engine.committed
    service.evidence.verify.assert_awaited_once()
