"""任务聚合有界读取；问题、假设和工具观测沿原Run重新核验来源。作者：yangaobo。"""

import json

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.repositories import tasks
from servicedesk_ai.schemas.diagnosis_views import PageView
from servicedesk_ai.schemas.task_views import (
    CheckView,
    TaskEventPage,
    TaskEventView,
    TaskFactView,
    TaskView,
)
from servicedesk_ai.schemas.tasks import HypothesisState, TaskEventDetails
from servicedesk_ai.services.conversations import ConversationService, instant
from servicedesk_ai.services.run_reads import RunReads
from servicedesk_ai.services.task_access import TaskAccess


class TaskReads:
    """读取不执行模型，也不因为工单权限开放其他拥有者的任务。"""

    def __init__(
        self,
        engine: AsyncEngine,
        access: TaskAccess,
        conversations: ConversationService,
        runs: RunReads,
    ) -> None:
        """共用所属资源和当前授权，不缓存知识撤权结果。"""
        self.engine, self.access, self.conversations, self.runs = (
            engine,
            access,
            conversations,
            runs,
        )

    async def authorized(self, actor: VerifiedIdentity, identifier: str) -> tasks.DiagnosisTask:
        """先查任务归属，再核当前身份和业务资源。"""
        async with self.engine.connect() as db:
            value = await tasks.task(db, identifier)
        await self.access.authorize(actor, value)
        return value

    async def get(self, actor: VerifiedIdentity, identifier: str) -> TaskView:
        """假设来源撤回时隐藏假设，不能用任务投影绕过运行正文授权。"""
        task = await self.authorized(actor, identifier)
        hidden = False
        for run_id in {item.proposed_run_id for item in task.hypothesis_state.items}:
            hidden |= (await self.runs.get(actor, run_id)).sources_hidden
        return TaskView(
            id=task.id,
            conversation_id=task.conversation_id,
            service_item_id=task.service_item_id,
            device_id=task.device_id,
            goal=task.goal,
            status=task.status,
            task_state_version=str(task.task_state_version),
            diagnosis_context_version=str(task.diagnosis_context_version),
            row_version=str(task.row_version),
            support_context=task.support_context,
            hypotheses=HypothesisState() if hidden else task.hypothesis_state,
            sources_hidden=hidden,
            blocking_draft_root_id=task.blocking_draft_root_id,
            handover_ticket_id=task.handover_ticket_id,
            outcome_source=task.outcome_source,
            outcome_note=task.outcome_note,
            created_at=instant(task.created_at),
            updated_at=instant(task.updated_at),
        )

    async def list(
        self, actor: VerifiedIdentity, conversation_id: str, page: int, size: int
    ) -> PageView[TaskView]:
        """同会话多个历史任务分页展示，活动任务由唯一约束保证。"""
        await self.conversations.authorized(actor, conversation_id)
        async with self.engine.connect() as db:
            count = (
                await db.execute(
                    text("SELECT COUNT(*) FROM ai_diagnosis_task WHERE conversation_id=:id"),
                    {"id": conversation_id},
                )
            ).scalar_one()
            ids = (
                (
                    await db.execute(
                        text(
                            "SELECT id FROM ai_diagnosis_task WHERE conversation_id=:id "
                            "ORDER BY created_at DESC,id LIMIT :limit OFFSET :offset"
                        ),
                        {"id": conversation_id, "limit": size, "offset": (page - 1) * size},
                    )
                )
                .scalars()
                .all()
            )
        return PageView(
            records=[await self.get(actor, identifier) for identifier in ids],
            total=str(count),
            page_num=page,
            page_size=size,
        )

    async def facts(
        self, actor: VerifiedIdentity, identifier: str, page: int, size: int
    ) -> PageView[TaskFactView]:
        """明确区分当前事实、更正历史及旧环境，模型假设不混入事实。"""
        task = await self.authorized(actor, identifier)
        async with self.engine.connect() as db:
            count = (
                await db.execute(
                    text("SELECT COUNT(*) FROM ai_diagnostic_fact WHERE task_id=:id"),
                    {"id": identifier},
                )
            ).scalar_one()
            rows = (
                (
                    await db.execute(
                        text("""
                SELECT f.id,f.state_version,f.fact_kind,f.operation,f.statement,f.source_type,
                    f.message_id,f.supersedes_fact_id,f.environment_snapshot,f.source_locator,f.created_at,c.run_id,
                    (f.operation<>'RETRACT' AND NOT EXISTS(SELECT 1 FROM ai_diagnostic_fact s
                        WHERE s.supersedes_fact_id=f.id)) AS active
                FROM ai_diagnostic_fact f LEFT JOIN ai_tool_call c ON c.id=f.source_tool_call_id
                WHERE f.task_id=:id ORDER BY f.state_version DESC,f.id LIMIT :limit OFFSET :offset
            """),
                        {"id": identifier, "limit": size, "offset": (page - 1) * size},
                    )
                )
                .mappings()
                .all()
            )
        records = []
        for row in rows:
            hidden = (
                (await self.runs.get(actor, row["run_id"])).sources_hidden
                if row["run_id"]
                else False
            )
            records.append(
                TaskFactView(
                    id=row["id"],
                    state_version=str(row["state_version"]),
                    fact_kind=row["fact_kind"],
                    operation=row["operation"],
                    statement=None if hidden else row["statement"],
                    reported_conditions=[]
                    if hidden
                    else json.loads(row["source_locator"]).get("reported_conditions", []),
                    source_type=row["source_type"],
                    message_id=row["message_id"],
                    supersedes_fact_id=row["supersedes_fact_id"],
                    active=bool(row["active"]),
                    historical_environment=json.loads(row["environment_snapshot"])[
                        "diagnosis_context_version"
                    ]
                    != task.diagnosis_context_version,
                    sources_hidden=hidden,
                    created_at=instant(row["created_at"]),
                )
            )
        return PageView(records=records, total=str(count), page_num=page, page_size=size)

    async def checks(
        self, actor: VerifiedIdentity, identifier: str, page: int, size: int
    ) -> PageView[CheckView]:
        """读取检查前复核提议Run，撤回资料不能经旧检查继续传播指导。"""
        await self.authorized(actor, identifier)
        async with self.engine.connect() as db:
            count = (
                await db.execute(
                    text("SELECT COUNT(*) FROM ai_diagnostic_check WHERE task_id=:id"),
                    {"id": identifier},
                )
            ).scalar_one()
            ids = (
                (
                    await db.execute(
                        text(
                            "SELECT id FROM ai_diagnostic_check WHERE task_id=:id "
                            "ORDER BY created_at DESC,id LIMIT :limit OFFSET :offset"
                        ),
                        {"id": identifier, "limit": size, "offset": (page - 1) * size},
                    )
                )
                .scalars()
                .all()
            )
            values = [await tasks.check(db, identifier, check_id) for check_id in ids]
        records = []
        for value in values:
            hidden = (await self.runs.get(actor, value.proposed_run_id)).sources_hidden
            records.append(
                CheckView(
                    id=value.id,
                    proposed_run_id=value.proposed_run_id,
                    check_key=value.check_key,
                    check_kind=value.check_kind,
                    attempt_number=value.attempt_number,
                    instruction=None if hidden else value.instruction,
                    answer_schema=None if hidden else value.answer_schema,
                    status=value.status,
                    executor_type=value.executor_type,
                    context_version=str(value.context_version),
                    row_version=str(value.row_version),
                    normalized_answer=value.normalized_answer,
                    answer_message_id=value.answer_message_id,
                    previous_check_id=value.previous_check_id,
                    sources_hidden=hidden,
                    expires_at=instant(value.expires_at),
                )
            )
        return PageView(records=records, total=str(count), page_num=page, page_size=size)

    async def events(
        self, actor: VerifiedIdentity, identifier: str, after: int, limit: int
    ) -> TaskEventPage:
        """事件同时受数量和字节限制；带旧假设的人工更正事件也重核其来源。"""
        await self.authorized(actor, identifier)
        async with self.engine.connect() as db:
            rows = (
                (
                    await db.execute(
                        text(
                            "SELECT id,state_version,actor_type,event_type,details,created_at "
                            "FROM ai_task_event WHERE task_id=:id AND state_version>:after "
                            "ORDER BY state_version LIMIT :limit"
                        ),
                        {"id": identifier, "after": after, "limit": limit + 1},
                    )
                )
                .mappings()
                .all()
            )
        records = []
        used = 0
        for row in rows[:limit]:
            details = TaskEventDetails.model_validate_json(row["details"])
            parents = {
                item.proposed_run_id
                for state in (details.hypotheses_before, details.hypotheses_after)
                if state
                for item in state.items
            }
            hidden = False
            for run_id in parents:
                hidden |= (await self.runs.get(actor, run_id)).sources_hidden
            item = TaskEventView(
                id=row["id"],
                state_version=str(row["state_version"]),
                actor_type=row["actor_type"],
                event_type=row["event_type"],
                details=None if hidden else details,
                sources_hidden=hidden,
                created_at=instant(row["created_at"]),
            )
            length = len(item.model_dump_json().encode())
            if used + length > 128 * 1024:
                break
            used += length
            records.append(item)
        return TaskEventPage(
            records=records,
            next_cursor=records[-1].state_version if records else str(after),
            has_more=len(rows) > len(records),
        )
