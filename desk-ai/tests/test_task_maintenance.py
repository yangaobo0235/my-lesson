"""维护对账的故障、锁后复核及重复提交边界；数据库约束另以真实MySQL验证。"""

from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import grpc
import pytest

from desk_contracts.ticket.v1.support_pb2 import DraftReceipt, DraftReconcileResult
from servicedesk_ai.repositories import drafts, tasks
from servicedesk_ai.services.task_maintenance import TaskMaintenance


class Engine:
    """仅替代连接生命周期，保留维护用例分支。"""

    @asynccontextmanager
    async def connect(self):
        """不模拟数据库并发结论。"""
        yield None

    begin = connect


@pytest.fixture
def maintenance(monkeypatch):
    """提供一个原拥有者的阻断根及可观察的状态写入边界。"""
    row = dict(
        id="a" * 32,
        draft_root_id="a" * 32,
        owner_id="b" * 32,
        task_id="c" * 32,
        conversation_id="d" * 32,
    )
    rpc = SimpleNamespace(
        ticket=SimpleNamespace(ReconcileDraftReceipt=AsyncMock()), options=lambda: {}
    )
    service = SimpleNamespace(access=SimpleNamespace(rpc=rpc), apply_receipt=AsyncMock())
    value = TaskMaintenance(Engine(), service)
    monkeypatch.setattr(drafts, "waiting_roots", AsyncMock(return_value=[row]))
    monkeypatch.setattr(drafts, "read_draft", AsyncMock(return_value=row))
    monkeypatch.setattr(drafts, "lock_draft", AsyncMock())
    monkeypatch.setattr(drafts, "releasable_root", AsyncMock(return_value=True))
    task = SimpleNamespace(
        id=row["task_id"],
        status="WAITING_APPROVAL",
        blocking_draft_root_id=row["id"],
        task_state_version=4,
        hypothesis_state=None,
        support_context=None,
        diagnosis_context_version=1,
    )
    monkeypatch.setattr(tasks, "task", AsyncMock(return_value=task))
    monkeypatch.setattr(tasks, "assert_idle", AsyncMock())
    monkeypatch.setattr(tasks, "open_check_count", AsyncMock(return_value=0))
    monkeypatch.setattr(tasks, "advance", AsyncMock())
    monkeypatch.setattr(tasks, "append_system_event", AsyncMock())
    monkeypatch.setattr("servicedesk_ai.services.task_maintenance.scope", AsyncMock())
    return value, rpc, service, task, row


@pytest.mark.asyncio
async def test_unavailable_receipt_does_not_release_wait(maintenance):
    """Java超时不能解释为未采用，保留原等待与版本。"""
    value, rpc, _, _, _ = maintenance
    rpc.ticket.ReconcileDraftReceipt.side_effect = grpc.aio.AioRpcError(
        grpc.StatusCode.UNAVAILABLE, grpc.aio.Metadata(), grpc.aio.Metadata()
    )
    assert await value.reconcile_drafts() == 0
    tasks.advance.assert_not_awaited()


@pytest.mark.asyncio
async def test_committed_receipt_wins_over_local_expiry(maintenance):
    """正式成功经同一采用用例提交，不能按本地过期释放为未执行。"""
    value, rpc, service, _, row = maintenance
    receipt = DraftReceipt(receipt_id="e" * 32, draft_root_id=row["id"])
    rpc.ticket.ReconcileDraftReceipt.return_value = DraftReconcileResult(receipt=receipt)
    assert await value.reconcile_drafts() == 1
    service.apply_receipt.assert_awaited_once_with(row, receipt)
    tasks.advance.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize("open_checks", [0, 1])
async def test_release_preserves_open_questions_and_records_uncertainty(maintenance, open_checks):
    """解除草稿等待后仍有待答项则继续等待用户，不产生虚构回答或自动运行。"""
    value, rpc, _, _, _ = maintenance
    rpc.ticket.ReconcileDraftReceipt.return_value = DraftReconcileResult()
    tasks.open_check_count.return_value = open_checks
    assert await value.reconcile_drafts() == 1
    expected = "WAITING_USER" if open_checks else "ACTIVE"
    assert tasks.advance.await_args.kwargs["status"] == expected
    event = tasks.append_system_event.await_args.args
    assert event[1] == "DRAFT_RELEASED" and "不推断在途动作失败" in event[2].reason


@pytest.mark.asyncio
async def test_lock_recheck_preserves_new_blocking_root(maintenance):
    """RPC查询期间切换了任务状态或根时，旧查询不能解除新的等待。"""
    value, rpc, _, task, _ = maintenance
    rpc.ticket.ReconcileDraftReceipt.return_value = DraftReconcileResult()
    task.blocking_draft_root_id = "f" * 32
    assert await value.reconcile_drafts() == 0
    tasks.advance.assert_not_awaited()


@pytest.mark.asyncio
async def test_live_validation_window_keeps_waiting(maintenance):
    """撤回后原证明窗口尚未结束，不提前放行后续任务。"""
    value, rpc, _, _, _ = maintenance
    rpc.ticket.ReconcileDraftReceipt.return_value = DraftReconcileResult()
    drafts.releasable_root.return_value = False
    assert await value.reconcile_drafts() == 0
    tasks.advance.assert_not_awaited()
