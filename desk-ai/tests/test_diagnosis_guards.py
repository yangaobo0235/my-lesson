"""私有会话、来源撤权、反馈竞争与执行权丢失的用例回归。作者：yangaobo。"""

import json
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock

import grpc
import pytest

from desk_contracts.common.v1.common_pb2 import ActorRef
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.diagnosis.execution import GenerationWorker
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import diagnosis as store
from servicedesk_ai.schemas.diagnosis import DiagnosisResult, FeedbackHandle, FeedbackShare
from servicedesk_ai.schemas.execution import GenerationContext, GenerationPipeline
from servicedesk_ai.services.conversations import ConversationService
from servicedesk_ai.services.evidence import Evidence, EvidenceService
from servicedesk_ai.services.feedback import FeedbackService
from servicedesk_ai.services.indexing import IndexWorker


class Engine:
    """记录事务是否正常提交；仓储替身不接触任何外部数据库。"""

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
    """固定本轮预算与环境，保持快照可序列化且不授予权限。"""
    return SimpleNamespace(
        id="d" * 32,
        conversation_id="b" * 32,
        owner_id="a" * 32,
        session_ref="session",
        auth_version=1,
        input_state_version=1,
        ticket_version=None,
        context_snapshot=GenerationContext(
            schema_version=1,
            mode="CUSTOMER_SELF_SERVICE",
            customer_id="c" * 32,
            service_item_id="e" * 32,
            device_id=None,
            product_model_id=None,
            ticket_id=None,
            environment={},
            ticket_title="",
            ticket_symptom="",
        ),
        pipeline_config=GenerationPipeline(
            schema_version=1,
            prompt_version="v1",
            tool_version="v1",
            model="model",
        ),
    )


@pytest.mark.parametrize("change", ["owner", "customer", "role"])
def test_private_conversation_does_not_follow_ticket_permission(change):
    """管理员及其他技术人员不能因工单权限取得他人私有对话。"""
    service = object.__new__(ConversationService)
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
async def test_transitive_fact_cannot_bypass_revoked_knowledge(monkeypatch):
    """历史助手事实传递依赖被撤权时，正文和草稿都不能经factId绕过。"""
    run = run_snapshot()
    rpc = SimpleNamespace(
        verify_sources=AsyncMock(
            side_effect=grpc.aio.AioRpcError(
                grpc.StatusCode.PERMISSION_DENIED,
                (),
                (),
                "denied",
            )
        )
    )
    service = EvidenceService(Engine(), rpc)
    fact = Evidence("fact-usage", 1, "DIAGNOSTIC_FACT", "事实", "摘要", {}, {}, fact_id="fact")
    source = Evidence(
        "knowledge-usage",
        2,
        "KNOWLEDGE_CHUNK",
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
                "source_type": "ASSISTANT_PROPOSAL",
                "source_generation_id": "parent",
            },
        ),
    )
    monkeypatch.setattr(store, "generation", AsyncMock(return_value=parent))
    with pytest.raises(BusinessError) as denied:
        await service.verify(run)
    assert denied.value.error == CommonErrorCode.FORBIDDEN
    rpc.verify_sources.assert_awaited_once()


@pytest.mark.asyncio
async def test_revocation_during_source_check_blocks_shared_body(monkeypatch):
    """网络复核期间撤销共享，最后交付前重查，不能继续返回旧正文。"""
    conversations = SimpleNamespace(evidence=SimpleNamespace(verify=AsyncMock(return_value=[])))
    service = FeedbackService(Engine(), conversations)
    row = {"generation_id": "run", "generation_status": "SUCCEEDED", "answer_text": "private"}
    monkeypatch.setattr(
        "servicedesk_ai.services.feedback.queries.shared_case", AsyncMock(side_effect=[row, None])
    )
    monkeypatch.setattr(store, "generation", AsyncMock(return_value=run_snapshot()))
    with pytest.raises(BusinessError) as denied:
        await service.shared_case(actor("KNOWLEDGE_ADMIN"), "feedback")
    assert denied.value.error == CommonErrorCode.NOT_FOUND


@pytest.mark.asyncio
@pytest.mark.parametrize("change", ["revoke", "version", "handled", "deleted", "private"])
async def test_feedback_handling_rechecks_locked_state(monkeypatch, change):
    """外部复核后必须重核共享、版本、删除及私有模式，竞争失败不写处理结论。"""
    service = FeedbackService(Engine(), None)
    service.shared_case = AsyncMock()
    row = {
        "share_with_knowledge_admin": True,
        "mode": "CUSTOMER_SELF_SERVICE",
        "deleted_at": None,
        "status": "PENDING",
        "row_version": 1,
    }
    row.update(
        {
            "revoke": {"share_with_knowledge_admin": False},
            "version": {"row_version": 2},
            "handled": {"status": "RESOLVED"},
            "deleted": {"deleted_at": datetime.now(UTC)},
            "private": {"mode": "TECHNICIAN_ASSIST"},
        }[change]
    )
    monkeypatch.setattr(
        "servicedesk_ai.services.feedback.queries.lock_feedback_case", AsyncMock(return_value=row)
    )
    write = AsyncMock()
    monkeypatch.setattr("servicedesk_ai.services.feedback.queries.handle_feedback", write)
    with pytest.raises(BusinessError):
        await service.handle(
            actor("KNOWLEDGE_ADMIN"),
            "feedback",
            FeedbackHandle(
                row_version="1",
                action="RESOLVE",
                handling_note="已核对",
            ),
        )
    write.assert_not_awaited()


@pytest.mark.asyncio
async def test_hidden_sources_still_allow_withdrawing_sharing(monkeypatch):
    """来源失权不阻止本人撤销共享，且不生成另一份反馈。"""
    conversations = SimpleNamespace(
        get_generation=AsyncMock(
            return_value=SimpleNamespace(conversation_id="conversation", sources_hidden=True)
        ),
        authorized=AsyncMock(return_value=SimpleNamespace(mode="CUSTOMER_SELF_SERVICE")),
    )
    service = FeedbackService(Engine(), conversations)
    service.view = AsyncMock(return_value="receipt")
    monkeypatch.setattr(
        "servicedesk_ai.services.feedback.queries.lock_feedback_sharing",
        AsyncMock(return_value=("feedback", 1, "RESOLVED", "UNHELPFUL")),
    )
    write = AsyncMock()
    monkeypatch.setattr("servicedesk_ai.services.feedback.queries.change_feedback_sharing", write)
    assert (
        await service.share(actor(), "run", FeedbackShare(row_version="1", share=False))
        == "receipt"
    )
    assert write.await_args.args[1] == {"id": "feedback", "share": False}


@pytest.mark.asyncio
async def test_old_execution_token_cannot_commit_late_answer(monkeypatch):
    """完整来源复核也不能替代最后事务内的执行权检查。"""
    worker = object.__new__(GenerationWorker)
    worker.engine = Engine()
    worker.rpc = SimpleNamespace(identity=SimpleNamespace(verify_ref=AsyncMock()))
    worker.verify_ticket = AsyncMock()
    worker.evidence = SimpleNamespace(verify=AsyncMock(return_value=[]))
    monkeypatch.setattr(
        store, "conversation", AsyncMock(return_value=SimpleNamespace(state_version=1))
    )
    monkeypatch.setattr(
        store, "owned", AsyncMock(side_effect=BusinessError(CommonErrorCode.CONFLICT))
    )
    write = AsyncMock()
    monkeypatch.setattr("servicedesk_ai.diagnosis.execution.queries.complete_generation", write)
    with pytest.raises(BusinessError):
        await worker.finish(
            run_snapshot(), DiagnosisResult(answer_type="CLARIFICATION", text="请补充"), None
        )
    write.assert_not_awaited()
    assert not worker.engine.committed


@pytest.mark.asyncio
async def test_typed_environment_and_unknown_metering_survive_commit(monkeypatch):
    """类型化环境仍可写入JSON；缺失计量保持未知，不能改为零。"""
    worker = object.__new__(GenerationWorker)
    worker.engine = Engine()
    worker.rpc = SimpleNamespace(identity=SimpleNamespace(verify_ref=AsyncMock()))
    worker.verify_ticket = AsyncMock()
    worker.evidence = SimpleNamespace(verify=AsyncMock(return_value=[]))
    monkeypatch.setattr(
        store, "conversation", AsyncMock(return_value=SimpleNamespace(state_version=1))
    )
    monkeypatch.setattr(store, "owned", AsyncMock())
    writes = {}
    for name in ("insert_generated_fact", "complete_generation", "advance_state"):
        writes[name] = AsyncMock()
        monkeypatch.setattr("servicedesk_ai.diagnosis.execution.queries." + name, writes[name])
    monkeypatch.setattr(
        "servicedesk_ai.diagnosis.execution.queries.call_usage",
        AsyncMock(return_value=[(None, None)]),
    )
    await worker.finish(
        run_snapshot(),
        DiagnosisResult(
            answer_type="CLARIFICATION",
            text="请补充",
            open_questions=["使用哪个系统？"],
        ),
        None,
    )
    locator = writes["insert_generated_fact"].await_args.args[1]["locator"]
    assert isinstance(json.loads(locator)["environment"], dict)
    assert writes["complete_generation"].await_args.args[1]["input"] is None
    assert worker.engine.committed


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
