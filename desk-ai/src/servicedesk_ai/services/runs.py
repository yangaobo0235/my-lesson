"""Run显式受理与取消；消息、固定输入清单和排队意图同事务保存。作者：yangaobo。"""

from __future__ import annotations

import hashlib
from datetime import timedelta
from decimal import Decimal

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from desk_contracts.ticket.v1.support_pb2 import SupportContext
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.infrastructure.run_quota import RunQuota
from servicedesk_ai.infrastructure.tracing import snapshot
from servicedesk_ai.repositories import runs as store
from servicedesk_ai.repositories import tasks
from servicedesk_ai.repositories.call_usage import require_budget, usage
from servicedesk_ai.repositories.conversation_scope import ConversationScope, scope
from servicedesk_ai.repositories.values import json_value, new_id
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.schemas.runs import (
    CheckReference,
    FactReference,
    MessageReference,
    RunContext,
    RunCreate,
    RunPipeline,
    TicketSnapshot,
)
from servicedesk_ai.services.task_access import TaskAccess
from servicedesk_ai.services.tasks import TERMINAL_STATUSES


class RunService:
    """受理锁序gate→conversation→task；外部身份/业务核验和Redis在事务外。"""

    def __init__(
        self, engine: AsyncEngine, access: TaskAccess, quota: RunQuota, settings: Settings
    ) -> None:
        """不持有模型客户端；Web受理成功只证明运行已排队。"""
        self.engine, self.access, self.quota, self.settings = engine, access, quota, settings

    async def authorized(self, actor: VerifiedIdentity, identifier: str) -> store.RunLease:
        """原回执也须当前私有范围授权；技术历史继续重核工单访问。"""
        async with self.engine.connect() as db:
            value = await store.run(db, identifier)
        conversation = await self.access.conversation(actor, value.conversation_id)
        if value.task_id is not None:
            async with self.engine.connect() as db:
                task = await tasks.task(db, value.task_id)
            await self.access.authorize(actor, task)
        elif conversation.mode != "CONSULT":
            raise BusinessError(CommonErrorCode.CONFLICT)
        return value

    async def replay(self, actor: VerifiedIdentity, key: str, digest: str) -> store.RunLease | None:
        """原键回执先于新执行资格、版本和额度检查。"""
        async with self.engine.connect() as db:
            old = await store.receipt(db, actor.actor.owner_id, key)
        if old is None:
            return None
        if old.request_hash != digest:
            raise BusinessError(CommonErrorCode.CONFLICT)
        return await self.authorized(actor, old.id)

    async def create(
        self, actor: VerifiedIdentity, conversation_id: str, form: RunCreate
    ) -> store.RunLease:
        """先保存反馈再显式续行；拒绝队列/预算时不会撤销原inputMessageId。"""
        digest = canonical_hash(
            {"conversation_id": conversation_id, **form.model_dump(mode="json")}
        )
        old = await self.replay(actor, form.client_request_id, digest)
        if old is not None:
            return old
        conversation = await self.access.conversation(actor, conversation_id)
        if (conversation.mode == "CONSULT") != (form.task_id is None):
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        external = None
        if form.task_id:
            async with self.engine.connect() as db:
                task = await tasks.task(db, form.task_id)
            if task.conversation_id != conversation_id:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            _, external = await self.access.authorize(actor, task, new_execution=True)
        await self.quota.reserve(
            actor.actor.owner_id,
            form.client_request_id,
            digest,
            consult=conversation.mode == "CONSULT",
        )
        async with self.engine.begin() as db:
            await store.lock_gate(db)
            conversation = await scope(db, conversation_id, lock=True)
            self.access.owner(actor, conversation)
            old_receipt = await store.receipt(db, actor.actor.owner_id, form.client_request_id)
            if old_receipt:
                if old_receipt.request_hash != digest:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                identifier = old_receipt.id
            else:
                identifier = await self.admit(db, actor, conversation, form, digest, external)
        return await self.authorized(actor, identifier)

    async def admit(
        self,
        db: AsyncConnection,
        actor: VerifiedIdentity,
        conversation: ConversationScope,
        form: RunCreate,
        digest: str,
        external: SupportContext | None,
    ) -> str:
        """在全局闸门内验证容量及固定输入，数据库唯一键提供最终并发约束。"""
        await tasks.assert_idle(db, conversation.id)
        if await store.queued_count(db) >= self.settings.execution.queue_capacity:
            raise BusinessError(RunErrorCode.RUN_QUEUE_FULL)
        task = None
        if form.task_id:
            task = await tasks.task(db, form.task_id, lock=True)
            if task.conversation_id != conversation.id:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            if (
                task.status in TERMINAL_STATUSES
                or task.status
                in {
                    "WAITING_APPROVAL",
                    "BLOCKED_EXTERNAL",
                }
                or task.task_state_version != int(form.task_state_version or "0")
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
            if await tasks.open_check_count(db, task.id):
                raise BusinessError(CommonErrorCode.CONFLICT)
            if external is None:
                raise BusinessError(CommonErrorCode.CONFLICT)
            if conversation.mode == "TECHNICIAN_ASSIST" and (
                external.diagnosis_context_version != task.diagnosis_context_version
                or (external.service_item_id or None) != task.service_item_id
                or (external.device_id or None) != task.device_id
                or (external.product_model_id or None) != task.product_model_id
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
            require_budget(await usage(db, task_id=task.id), task.budget_policy, cost=Decimal(0))
        if form.resumes_run_id:
            previous = await store.run(db, form.resumes_run_id)
            if previous.conversation_id != conversation.id or previous.task_id != form.task_id:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            if previous.status not in {"FAILED", "CANCELLED", "INTERRUPTED"}:
                raise BusinessError(CommonErrorCode.CONFLICT)
        message_id = await self.input_message(db, conversation.id, form, digest)
        manifest = await self.manifest(db, conversation, task, message_id)
        if conversation.ticket_id and external:
            manifest = RunContext.model_validate(
                manifest.model_dump()
                | {
                    "ticket_snapshot": TicketSnapshot(
                        title=external.title,
                        symptom=external.symptom,
                        attempted_actions=external.attempted_actions or None,
                        ticket_row_version=external.ticket_row_version,
                        ticket_event_sequence=external.ticket_event_sequence,
                    )
                }
            )
        now = await tasks.decision_time(db)
        execution = self.settings.execution.model_dump()
        if conversation.mode == "CONSULT":
            execution["max_model_calls"] = min(execution["max_model_calls"], 2)
        pipeline = RunPipeline(
            **execution,
            prompt_version="desk-v59-no-progress-finalization",
            tool_version="readonly-v46-source-provenance",
            model=self.settings.model.chat_model,
        )
        identifier = new_id()
        trace = snapshot()
        await db.execute(
            text("""
            INSERT INTO ai_run(id,conversation_id,task_id,owner_id,input_message_id,resumes_run_id,
              client_request_id,request_hash,input_state_version,diagnosis_context_version,
              ticket_row_version,ticket_event_sequence,session_ref,auth_version,pipeline_config,
              context_manifest,trace_context,deadline_at,created_at,updated_at)
            VALUES (:id,:conversation,:task,:owner,:message,:resumes,:key,:hash,:state,:context,
              :ticket_version,:sequence,:session,:auth,:pipeline,:manifest,:trace,:deadline,:now,:now)
        """),
            {
                "id": identifier,
                "conversation": conversation.id,
                "task": form.task_id,
                "owner": actor.actor.owner_id,
                "message": message_id,
                "resumes": form.resumes_run_id,
                "key": form.client_request_id,
                "hash": digest,
                "state": task.task_state_version if task else None,
                "context": task.diagnosis_context_version if task else None,
                "ticket_version": external.ticket_row_version
                if conversation.ticket_id and external
                else None,
                "sequence": external.ticket_event_sequence
                if conversation.ticket_id and external
                else None,
                "session": actor.actor.session_ref,
                "auth": actor.actor.auth_version,
                "pipeline": pipeline.model_dump_json(),
                "manifest": manifest.model_dump_json(),
                "trace": json_value(trace) if trace else None,
                "deadline": now + timedelta(seconds=pipeline.total_seconds),
                "now": now,
            },
        )
        return identifier

    async def input_message(
        self, db: AsyncConnection, conversation_id: str, form: RunCreate, digest: str
    ) -> str:
        """现有输入只允许同会话同任务USER消息，不重复保存检查反馈。"""
        if form.input_message_id:
            row = (
                await db.execute(
                    text("""
                SELECT task_id,role FROM ai_message WHERE id=:id AND conversation_id=:conversation
            """),
                    {"id": form.input_message_id, "conversation": conversation_id},
                )
            ).first()
            if row is None or row[0] != form.task_id or row[1] != "USER":
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            return form.input_message_id
        if form.message is None:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        identifier = new_id()
        await db.execute(
            text("""
            UPDATE ai_conversation SET message_sequence=message_sequence+1,
              updated_at=UTC_TIMESTAMP(6) WHERE id=:id
        """),
            {"id": conversation_id},
        )
        await db.execute(
            text("""
            INSERT INTO ai_message(id,conversation_id,task_id,sequence_number,role,content,
                                   content_sha256,client_request_id,request_hash)
            SELECT :id,id,:task,message_sequence,'USER',:content,:sha,:key,:hash
            FROM ai_conversation WHERE id=:conversation
        """),
            {
                "id": identifier,
                "conversation": conversation_id,
                "task": form.task_id,
                "content": form.message,
                "sha": hashlib.sha256(form.message.encode()).hexdigest(),
                "key": form.client_request_id,
                "hash": digest,
            },
        )
        return identifier

    async def manifest(
        self,
        db: AsyncConnection,
        conversation: ConversationScope,
        task: tasks.DiagnosisTask | None,
        message_id: str,
    ) -> RunContext:
        """固定有界输入身份；worker在实际纳入历史前登记并重核传递来源。"""
        missing: list[str] = []
        messages = (
            (
                await db.execute(
                    text("""
            SELECT id,content_sha256 FROM ai_message WHERE conversation_id=:conversation
              AND task_id <=> :task AND role IN ('USER','ASSISTANT')
            ORDER BY sequence_number DESC LIMIT 21
        """),
                    {"conversation": conversation.id, "task": task.id if task else None},
                )
            )
            .mappings()
            .all()
        )
        if len(messages) > 20:
            missing.append("较早聊天未纳入本轮，不能假定已读取完整历史")
        references = [MessageReference(**row) for row in reversed(messages[:20])]
        if message_id not in {item.id for item in references}:
            row = (
                (
                    await db.execute(
                        text("SELECT id,content_sha256 FROM ai_message WHERE id=:id"),
                        {"id": message_id},
                    )
                )
                .mappings()
                .one()
            )
            references = references[-19:] + [MessageReference(**row)]
        facts: list[FactReference] = []
        checks: list[CheckReference] = []
        if task:
            rows = (
                (
                    await db.execute(
                        text("""
                SELECT f.id,f.state_version FROM ai_diagnostic_fact f WHERE f.task_id=:task
                  AND f.operation<>'RETRACT'
                  AND NOT EXISTS(SELECT 1 FROM ai_diagnostic_fact s WHERE s.supersedes_fact_id=f.id)
                ORDER BY f.state_version DESC,f.id LIMIT :limit
            """),
                        {"task": task.id, "limit": self.settings.execution.facts_per_round + 1},
                    )
                )
                .mappings()
                .all()
            )
            if len(rows) > self.settings.execution.facts_per_round:
                missing.append("较早事实未纳入本轮，不能据缺失推断不存在")
            facts = [
                FactReference(**row) for row in rows[: self.settings.execution.facts_per_round]
            ]
            rows = (
                (
                    await db.execute(
                        text("""
                SELECT id,row_version,context_version FROM ai_diagnostic_check WHERE task_id=:task
                ORDER BY (status IN ('OPEN','NEEDS_RECHECK')) DESC,created_at DESC,id LIMIT 65
            """),
                        {"task": task.id},
                    )
                )
                .mappings()
                .all()
            )
            if len(rows) > 64:
                missing.append("较早检查未纳入本轮，重复检查仍由持久记录校验")
            checks = [CheckReference(**row) for row in rows[:64]]
        values = {"hypotheses": task.hypothesis_state} if task else {}
        return RunContext(
            mode=conversation.mode,
            customer_id=conversation.customer_id,
            service_item_id=task.service_item_id if task else None,
            device_id=task.device_id if task else None,
            product_model_id=task.product_model_id if task else None,
            ticket_id=conversation.ticket_id,
            support_context=task.support_context if task else None,
            goal=task.goal if task else None,
            messages=references,
            facts=facts,
            checks=checks,
            missing_context=missing,
            **values,
        )

    async def cancel(self, actor: VerifiedIdentity, identifier: str) -> store.RunLease:
        """取消先提交持久终态，旧worker不能凭迟到输出覆盖取消。"""
        value = await self.authorized(actor, identifier)
        async with self.engine.begin() as db:
            conversation = await scope(db, value.conversation_id, lock=True)
            self.access.owner(actor, conversation)
            if value.task_id:
                await tasks.task(db, value.task_id, lock=True)
            await store.stop(db, identifier, "CANCELLED", None)
        return await self.authorized(actor, identifier)
