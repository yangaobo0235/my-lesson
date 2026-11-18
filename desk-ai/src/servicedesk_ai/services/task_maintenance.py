"""有界收敛过期待答项，不等待用户、不自动调用模型或改变正式工单。作者：yangaobo。"""

import logging

import grpc
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.ticket.v1.support_pb2 import DraftReconcileRequest
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import drafts as draft_queries
from servicedesk_ai.repositories import tasks
from servicedesk_ai.repositories.conversation_scope import scope
from servicedesk_ai.repositories.values import new_id
from servicedesk_ai.schemas.tasks import TaskEventDetails, TaskReceipt, TaskStatus
from servicedesk_ai.services.drafts import DraftService

LOGGER = logging.getLogger(__name__)


class TaskMaintenance:
    """会话→任务→检查锁序与人工反馈一致，多个worker重复扫描只提交一次事件。"""

    def __init__(self, engine: AsyncEngine, drafts: DraftService) -> None:
        """只复用AI数据库，不持有用户身份或外部业务写接口。"""
        self.engine = engine
        self.drafts = drafts
        self.after_root = ""

    async def reconcile_drafts(self) -> int:
        """按最小只读服务合同核对正式采用；依赖不可查时保持等待，下一批再查。"""
        async with self.engine.connect() as db:
            candidates = await draft_queries.waiting_roots(db, self.after_root)
        self.after_root = candidates[-1]["id"] if candidates else ""
        changed = 0
        for candidate in candidates:
            try:
                rpc = self.drafts.access.rpc
                result = await rpc.ticket.ReconcileDraftReceipt(
                    DraftReconcileRequest(
                        owner_id=candidate["owner_id"], draft_root_id=candidate["draft_root_id"]
                    ),
                    **rpc.options(),
                )
                async with self.engine.connect() as db:
                    row = await draft_queries.read_draft(db, {"id": candidate["id"]})
                if row is None:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                if result.HasField("receipt"):
                    await self.drafts.apply_receipt(row, result.receipt)
                    changed += 1
                else:
                    changed += int(
                        await self.release_wait(
                            row["task_id"], row["conversation_id"], row["draft_root_id"]
                        )
                    )
            except grpc.aio.AioRpcError as error:
                LOGGER.warning(
                    "草稿回执待核实 taskId=%s rpcStatus=%s", candidate["task_id"], error.code().name
                )
            except BusinessError as error:
                if error.error != CommonErrorCode.CONFLICT:
                    LOGGER.error(
                        "草稿回执核对失败 taskId=%s errorCode=%s",
                        candidate["task_id"],
                        error.error.code,
                    )
        return changed

    async def release_wait(self, task_id: str, conversation_id: str, root: str) -> bool:
        """解除已失效草稿的等待，不标记未采用、不运行模型，迟到正式回执仍可同步。"""
        async with self.engine.begin() as db:
            await scope(db, conversation_id, lock=True)
            task = await tasks.task(db, task_id, lock=True)
            await tasks.assert_idle(db, conversation_id)
            await draft_queries.lock_draft(db, root)
            if task.status != "WAITING_APPROVAL" or task.blocking_draft_root_id != root:
                return False
            if not await draft_queries.releasable_root(db, root):
                return False
            status: TaskStatus = (
                "WAITING_USER" if await tasks.open_check_count(db, task.id) else "ACTIVE"
            )
            await tasks.append_system_event(
                db,
                "DRAFT_RELEASED",
                TaskEventDetails(
                    receipt=TaskReceipt(
                        task_id=task.id,
                        event_id=new_id(),
                        task_state_version=str(task.task_state_version + 1),
                        status=status,
                    ),
                    draft_root_id=root,
                    reason="草稿已失效，本次未查到已提交回执，解除等待；不推断在途动作失败，正式结果仍以Java回执为准",
                ),
            )
            await tasks.advance(
                db,
                task,
                status=status,
                hypotheses=task.hypothesis_state,
                context=task.support_context,
                context_version=task.diagnosis_context_version,
            )
            return True

    async def expire_checks(self) -> int:
        """每批最多20个任务、每任务100个检查；锁后再读取数据库决策时刻。"""
        async with self.engine.connect() as db:
            candidates = await tasks.expiry_candidates(db)
        changed = 0
        for identifier, conversation_id in candidates:
            async with self.engine.begin() as db:
                # 归档任务也需清理到期检查；这里不开放任何用户读取能力。
                await scope(db, conversation_id, lock=True)
                task = await tasks.task(db, identifier, lock=True)
                try:
                    await tasks.assert_idle(db, conversation_id)
                except BusinessError as failure:
                    if failure.error != CommonErrorCode.CONFLICT:
                        raise
                    continue
                now = await tasks.decision_time(db)
                ids = await tasks.expire_due_checks(db, identifier, now)
                if not ids:
                    continue
                status: TaskStatus = task.status
                if status == "WAITING_USER" and await tasks.open_check_count(db, identifier) == 0:
                    status = "ACTIVE"
                details = TaskEventDetails(
                    receipt=TaskReceipt(
                        task_id=identifier,
                        event_id=new_id(),
                        task_state_version=str(task.task_state_version + 1),
                        status=status,
                    ),
                    reason="待答项已过期；未推断执行或恢复，重新排障需明确续行",
                    changed_check_ids=ids,
                )
                await tasks.append_expiry_event(db, details)
                await tasks.advance(
                    db,
                    task,
                    status=status,
                    hypotheses=task.hypothesis_state,
                    context=task.support_context,
                    context_version=task.diagnosis_context_version,
                    closed_at=task.closed_at,
                    outcome_source=task.outcome_source,
                    outcome_note=task.outcome_note,
                )
                changed += len(ids)
        return changed
