"""草稿确认绑定不可变内容、当前修订与版本，内部证明具有严格期限。作者：yangaobo。"""

from contextlib import asynccontextmanager
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from desk_contracts.ai.v1.execution_pb2 import ValidateDraftRequest
from desk_contracts.ticket.v1.support_pb2 import DraftReceipt
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import drafts as queries
from servicedesk_ai.repositories import tasks as store
from servicedesk_ai.schemas.diagnosis import DraftContent, DraftCreate
from servicedesk_ai.schemas.diagnosis_views import DraftView
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.schemas.tasks import TaskEventDetails, TaskReceipt
from servicedesk_ai.services.drafts import DraftService


class Engine:
    """只替代事务资源；数据库唯一约束和并发另在独占MySQL验证。"""

    @asynccontextmanager
    async def connect(self):
        """提供查询和事务资源的生命周期。"""
        yield None

    begin = connect


@pytest.fixture
def confirmation(monkeypatch):
    """固定已通过来源授权的修订，保留最后持锁状态检查的真实用例。"""
    now = datetime(2026, 11, 16, 9)
    content = DraftContent(content="已核对打印队列")
    draft = DraftView(
        id="a" * 32,
        draft_root_id="b" * 32,
        revision_number=2,
        content_sha256=canonical_hash(content.model_dump()),
        status="PENDING",
        source_run_id="c" * 32,
        purpose="ADD_RECORD",
        ticket_id="d" * 32,
        ticket_row_version="7",
        task_state_version="3",
        task_id="1" * 32,
        diagnosis_context_version="2",
        content=content,
        expires_at=now.replace(tzinfo=UTC) + timedelta(hours=1),
    )
    conversation = SimpleNamespace(customer_id="e" * 32)
    task = SimpleNamespace(id="1" * 32, conversation_id="f" * 32, task_state_version=3)
    conversations = SimpleNamespace(
        authorize=AsyncMock(return_value=(conversation, None)),
        rpc=SimpleNamespace(identity=SimpleNamespace(verify_ref=AsyncMock())),
    )
    service = DraftService(Engine(), conversations, None)
    service.get = AsyncMock(return_value=draft)
    row = {"status": "PENDING", "expires_at": now + timedelta(hours=1)}
    monkeypatch.setattr(queries, "draft_conversation", AsyncMock(return_value="f" * 32))
    monkeypatch.setattr(queries, "lock_draft", AsyncMock())
    monkeypatch.setattr(queries, "read_draft", AsyncMock(return_value=row))
    monkeypatch.setattr(queries, "database_now", AsyncMock(return_value=now))
    monkeypatch.setattr(store, "task", AsyncMock(return_value=task))
    monkeypatch.setattr(
        "servicedesk_ai.services.drafts.scope", AsyncMock(return_value=conversation)
    )
    monkeypatch.setattr(store, "assert_idle", AsyncMock())
    request = ValidateDraftRequest(
        draft_id=draft.id,
        purpose=draft.purpose,
        customer_id=conversation.customer_id,
        ticket_id=draft.ticket_id,
        ticket_row_version=7,
        content_sha256=draft.content_sha256,
    )
    return service, request, row, task, now


@pytest.mark.asyncio
async def test_proof_binds_root_content_and_thirty_second_window(confirmation):
    """根来自持久修订，证明截止不超过30秒或草稿期限。"""
    service, request, row, _, now = confirmation
    row["expires_at"] = now + timedelta(seconds=8)
    result = await service.validate(request)
    assert result.draft_root_id == "b" * 32
    assert result.id != result.draft_root_id
    assert result.content_sha256 == request.content_sha256
    assert result.valid_until_epoch_ms - result.validated_at_epoch_ms == 8000
    assert len(result.validation_id) == 32


@pytest.mark.asyncio
async def test_confirmation_digest_cannot_be_replaced(confirmation):
    """浏览器提供另一份内容摘要时不会获得证明。"""
    service, request, _, _, _ = confirmation
    request.content_sha256 = "0" * 64
    with pytest.raises(BusinessError) as error:
        await service.validate(request)
    assert error.value.error == CommonErrorCode.CONFLICT


@pytest.mark.asyncio
@pytest.mark.parametrize("status", ["WITHDRAWN", "SUPERSEDED", "EXPIRED"])
async def test_status_change_during_source_check_prevents_proof(confirmation, status):
    """来源检查期间发生撤回或编辑，最后锁内检查拒绝旧读。"""
    service, request, row, _, _ = confirmation
    row["status"] = status
    with pytest.raises(BusinessError):
        await service.validate(request)


@pytest.mark.asyncio
async def test_fact_change_during_source_check_prevents_proof(confirmation):
    """版本检查必须发生在来源检查之后的最终事务内。"""
    service, request, _, conversation, _ = confirmation
    conversation.task_state_version = 4
    with pytest.raises(BusinessError):
        await service.validate(request)


@pytest.mark.parametrize(
    ("purpose", "content"),
    [
        ("ADD_RECORD", DraftContent(content="核查完成", resolution_type="RESTORED")),
        ("RESOLVE_TICKET", DraftContent(content="已转介", resolution_type="REFERRED")),
        ("CREATE_TICKET", DraftContent(title="网络问题", symptom=" ")),
    ],
)
def test_revision_rejects_inapplicable_or_incomplete_fields(purpose, content):
    """无关用途字段和不完整解决结论不能成为确认修订。"""
    with pytest.raises(BusinessError):
        DraftService.validate_content(purpose, content)


def test_new_optional_fields_preserve_existing_content_digest():
    """未使用预约和跟进字段的既有修订，不因模型新增字段导致摘要失配。"""
    original = {
        "schema_version": 1,
        "title": "",
        "symptom": "",
        "attempted_actions": "",
        "content": "已核对日志",
        "resolution_type": None,
        "referral_target": None,
    }
    assert canonical_hash(
        DraftContent.model_validate(original).model_dump(mode="json")
    ) == canonical_hash(original)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "purpose",
    [
        "CREATE_TICKET",
        "ADD_RECORD",
        "RESOLVE_TICKET",
        "REQUEST_INFORMATION",
        "BOOK_ONSITE",
    ],
)
async def test_reconciliation_uses_action_type_not_timeline_event(monkeypatch, purpose):
    """Java正式回执使用动作枚举，不能把COMMENTED/NEED_INFO等时间线事件当动作。"""
    row = dict(
        id="a" * 32,
        draft_root_id="a" * 32,
        task_id="b" * 32,
        conversation_id="c" * 32,
        ticket_id=None if purpose == "CREATE_TICKET" else "d" * 32,
        purpose=purpose,
    )
    official = DraftReceipt(
        receipt_id="e" * 32,
        draft_root_id=row["draft_root_id"],
        draft_id=row["id"],
        action_type=purpose,
        ticket_id="d" * 32,
        result_code="APPLIED",
    )
    receipt = TaskReceipt(
        task_id=row["task_id"], event_id="f" * 32, task_state_version="5", status="ACTIVE"
    )
    monkeypatch.setattr("servicedesk_ai.services.drafts.scope", AsyncMock())
    monkeypatch.setattr(store, "task", AsyncMock(return_value=SimpleNamespace(id=row["task_id"])))
    monkeypatch.setattr(store, "assert_idle", AsyncMock())
    monkeypatch.setattr(
        queries,
        "synchronized_receipt",
        AsyncMock(return_value=TaskEventDetails(reason="已同步正式回执", receipt=receipt)),
    )
    service = DraftService(Engine(), None, None)
    assert await service.apply_receipt(row, official) == receipt
    if purpose == "REQUEST_INFORMATION":
        official.action_type = "NEED_INFO"
        with pytest.raises(BusinessError) as error:
            await service.apply_receipt(row, official)
        assert error.value.error == CommonErrorCode.UPSTREAM_PROTOCOL_ERROR


@pytest.mark.parametrize(
    "purpose,content",
    [
        ("REQUEST_INFORMATION", DraftContent(content="请提供错误提示")),
        ("BOOK_ONSITE", DraftContent(slot_id="a" * 32, content="夹带未确认内容")),
        ("ADD_RECORD", DraftContent(content="记录", slot_id="a" * 32)),
        ("CREATE_TICKET", DraftContent(title="标题" * 51, symptom="无法打印")),
    ],
)
def test_new_action_fields_are_required_and_purpose_bound(purpose, content):
    """时间、时段和标题遵守正式动作合同，不接收其他用途字段。"""
    with pytest.raises(BusinessError):
        DraftService.validate_content(purpose, content)


def test_information_and_booking_drafts_use_explicit_human_selection():
    """人选时刻与时段可以形成草稿；预约不要求成功模型运行。"""
    information = DraftContent(content="请提供错误提示", follow_up_at="2026-11-18T09:00:00+08:00")
    DraftService.validate_content("REQUEST_INFORMATION", information)
    assert DraftContent.model_validate_json(information.model_dump_json()) == information
    DraftService.validate_content("BOOK_ONSITE", DraftContent(slot_id="a" * 32))
    assert (
        DraftCreate(
            purpose="BOOK_ONSITE", slot_id="a" * 32, client_request_id="b" * 32
        ).source_run_id
        is None
    )
    with pytest.raises(ValueError):
        DraftCreate(
            purpose="BOOK_ONSITE",
            slot_id="a" * 32,
            client_request_id="b" * 32,
            wait_for_confirmation=True,
        )
