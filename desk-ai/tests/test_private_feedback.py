"""验证本人评价的授权、失败运行、版本竞争及已移除的聊天共享入口。作者：yangaobo。"""

from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest

from desk_contracts.common.v1.common_pb2 import ActorRef
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.main import create_app
from servicedesk_ai.repositories import feedback as queries
from servicedesk_ai.repositories import runs as store
from servicedesk_ai.schemas.diagnosis import FeedbackInput
from servicedesk_ai.schemas.diagnosis_views import FeedbackView
from servicedesk_ai.services.conversations import ConversationService
from servicedesk_ai.services.feedback import FeedbackService
from servicedesk_ai.services.task_access import TaskAccess


class Engine:
    """隔离事务替身，只记录用例是否成功提交。"""

    def __init__(self):
        self.committed = False

    @asynccontextmanager
    async def connect(self):
        yield object()

    @asynccontextmanager
    async def begin(self):
        yield object()
        self.committed = True


@pytest.fixture
def context(monkeypatch):
    """保留真实拥有者规则，仓储与外部当前授权在此隔离。"""
    actor = VerifiedIdentity(
        actor=ActorRef(owner_id="a" * 32), organization_id="b" * 32, roles=["CUSTOMER_USER"]
    )
    conversation = SimpleNamespace(
        id="c" * 32,
        owner_id=actor.actor.owner_id,
        customer_id=actor.organization_id,
        mode="CUSTOMER_SELF_SERVICE",
    )
    run = SimpleNamespace(
        id="d" * 32,
        conversation_id=conversation.id,
        owner_id=actor.actor.owner_id,
        status="FAILED",
    )
    conversations = object.__new__(ConversationService)
    conversations.access = object.__new__(TaskAccess)
    conversations.authorized = AsyncMock(return_value=conversation)
    service = FeedbackService(Engine(), conversations)
    monkeypatch.setattr(store, "run", AsyncMock(return_value=run))
    monkeypatch.setattr(
        "servicedesk_ai.services.feedback.scope", AsyncMock(return_value=conversation)
    )
    saved = FeedbackView(
        id="e" * 32,
        run_id=run.id,
        rating="UNHELPFUL",
        adoption="NOT_USED",
        issue_category="OTHER",
        comment=None,
        row_version="0",
    )
    read, insert, update = AsyncMock(return_value=saved), AsyncMock(), AsyncMock()
    monkeypatch.setattr(queries, "read_feedback", read)
    monkeypatch.setattr(queries, "insert_feedback", insert)
    monkeypatch.setattr(queries, "update_feedback", update)
    return SimpleNamespace(
        service=service,
        actor=actor,
        run=run,
        conversation=conversation,
        saved=saved,
        read=read,
        insert=insert,
        update=update,
        form=FeedbackInput(rating="UNHELPFUL", adoption="NOT_USED", issue_category="OTHER"),
    )


@pytest.mark.asyncio
@pytest.mark.parametrize("status", ["SUCCEEDED", "FAILED", "INTERRUPTED", "CANCELLED"])
async def test_terminal_feedback_does_not_read_answer_or_sources(context, status):
    """失败和来源已隐藏仍可评价，不以回答可读或成功作为前提。"""
    c = context
    c.run.status = status
    c.read.side_effect = [None, c.saved]
    assert await c.service.put(c.actor, c.run.id, c.form) == c.saved
    c.insert.assert_awaited_once()
    assert c.service.engine.committed


@pytest.mark.asyncio
async def test_same_content_retry_keeps_original_version(context):
    """响应丢失后同内容原样重试不产生新版本。"""
    c = context
    assert await c.service.put(c.actor, c.run.id, c.form) == c.saved
    c.insert.assert_not_awaited()
    c.update.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize("version", [None, "9"])
async def test_changed_content_requires_current_version(context, version):
    c = context
    form = c.form.model_copy(update={"adoption": "REJECTED", "row_version": version})
    with pytest.raises(BusinessError) as error:
        await c.service.put(c.actor, c.run.id, form)
    assert error.value.error == CommonErrorCode.CONFLICT
    c.update.assert_not_awaited()


@pytest.mark.asyncio
async def test_current_version_updates_only_own_feedback(context):
    c = context
    form = c.form.model_copy(update={"adoption": "REJECTED", "row_version": "0"})
    await c.service.put(c.actor, c.run.id, form)
    assert c.update.await_args.args[1:3] == (c.saved.id, 0)
    assert c.update.await_args.args[3]["adoption"] == "REJECTED"


@pytest.mark.asyncio
@pytest.mark.parametrize("change", ["owner", "role", "revoked", "active", "run_owner"])
async def test_authorization_and_terminal_state_precede_feedback_write(context, change):
    c = context
    if change == "owner":
        c.conversation.owner_id = "f" * 32
    elif change == "role":
        c.actor.roles[:] = ["KNOWLEDGE_ADMIN"]
    elif change == "revoked":
        c.service.conversations.authorized.side_effect = BusinessError(CommonErrorCode.NOT_FOUND)
    elif change == "active":
        c.run.status = "RUNNING"
    else:
        c.run.owner_id = "f" * 32
    with pytest.raises(BusinessError):
        await c.service.put(c.actor, c.run.id, c.form)
    c.insert.assert_not_awaited()
    c.update.assert_not_awaited()
    assert not c.service.engine.committed


@pytest.mark.asyncio
async def test_get_unsubmitted_feedback_returns_null(context):
    c = context
    c.read.return_value = None
    assert await c.service.get(c.actor, c.run.id) is None
    c.service.conversations.authorized.assert_awaited_once_with(c.actor, c.conversation.id)


@pytest.mark.asyncio
async def test_sql_cas_failure_is_not_reported_as_saved():
    db = SimpleNamespace(execute=AsyncMock(return_value=SimpleNamespace(rowcount=0)))
    with pytest.raises(BusinessError) as error:
        await queries.update_feedback(db, "a" * 32, 0, {})
    assert error.value.error == CommonErrorCode.CONFLICT


@pytest.mark.asyncio
async def test_shared_chat_routes_are_not_exposed():
    """实际ASGI路由已移除，不能用遗留URL绕过私有评价边界。"""
    app = create_app(Settings(infrastructure_enabled=False, environment="dev"))
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        for method, path in [
            ("GET", "/api/v1/admin/feedback"),
            ("GET", "/api/v1/admin/feedback/" + "a" * 32),
            ("POST", "/api/v1/admin/feedback/" + "a" * 32 + "/handle"),
            ("PATCH", "/api/v1/ai/generations/" + "a" * 32 + "/feedback/share"),
        ]:
            response = await client.request(method, path)
            assert response.status_code == 404
    paths = app.openapi()["paths"]
    assert set(paths["/api/v1/ai/runs/{identifier}/feedback"]) == {"get", "put"}
