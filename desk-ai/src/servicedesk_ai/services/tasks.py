"""人工事实、检查反馈与任务终态的原子用例；回复不会隐式排队付费运行。作者：yangaobo。"""

from __future__ import annotations

import hashlib
from dataclasses import replace
from datetime import UTC
from typing import Literal

from google.protobuf.json_format import MessageToDict
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from desk_contracts.ticket.v1.support_pb2 import SupportContext
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import runs
from servicedesk_ai.repositories import tasks as store
from servicedesk_ai.repositories.conversation_scope import ConversationScope, scope
from servicedesk_ai.repositories.values import new_id
from servicedesk_ai.schemas.diagnosis import SupportEnvironment
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.schemas.tasks import (
    CheckAnswerInput,
    ContextChange,
    EnvironmentField,
    FieldSource,
    HypothesisState,
    NormalizedAnswer,
    TaskAction,
    TaskBudget,
    TaskClose,
    TaskContextChange,
    TaskCreate,
    TaskEnvironment,
    TaskEventDetails,
    TaskFactChange,
    TaskReceipt,
    TaskStatus,
)
from servicedesk_ai.services.evidence import EvidenceService
from servicedesk_ai.services.task_access import TaskAccess

TERMINAL_STATUSES = {"RESOLVED_REPORTED", "HANDED_OFF", "CLOSED", "CANCELLED"}


def invalidate_hypotheses(value: HypothesisState, fact_id: str | None = None) -> HypothesisState:
    """更正事实仅失效受影响项；环境变化失效全部，保留身份和原依据供事件审阅。"""
    return HypothesisState(
        items=[
            item.model_copy(
                update={
                    "status": "INAPPLICABLE",
                    "supporting_fact_ids": [
                        identifier
                        for identifier in item.supporting_fact_ids
                        if identifier != fact_id
                    ],
                    "opposing_fact_ids": [
                        identifier for identifier in item.opposing_fact_ids if identifier != fact_id
                    ],
                }
            )
            if fact_id is None
            or fact_id in item.supporting_fact_ids
            or fact_id in item.opposing_fact_ids
            else item
            for item in value.items
        ]
    )


def action_hash(kind: str, target: str, form: TaskAction) -> str:
    """原请求版本也属于幂等语义，不能换版本复用同一个动作键。"""
    return canonical_hash(
        {
            "operation": kind,
            "target": target,
            "input": form.model_dump(mode="json", exclude={"client_action_id"}),
        }
    )


def manual_source(conversation: ConversationScope) -> Literal["USER_REPORT", "HUMAN_RECORD"]:
    """来源由已授权会话模式决定，客户端不能自封工具或技术记录。"""
    return "USER_REPORT" if conversation.mode == "CUSTOMER_SELF_SERVICE" else "HUMAN_RECORD"


class TaskService:
    """所有人工修改锁conversation→task→check，并与消息/事实/原回执一起提交。"""

    def __init__(
        self,
        engine: AsyncEngine,
        access: TaskAccess,
        budget: TaskBudget,
        evidence: EvidenceService,
    ) -> None:
        """依赖只有数据库与当前领域授权，不持有模型客户端。"""
        self.engine, self.access, self.budget = engine, access, budget
        self.evidence = evidence

    async def create(
        self, actor: VerifiedIdentity, conversation_id: str, form: TaskCreate
    ) -> TaskReceipt:
        """创建一个固定目标，目标原消息、初始事实及版本1事件在同一事务写入。"""
        conversation = await self.access.conversation(actor, conversation_id)
        digest = canonical_hash(
            {
                "conversation": conversation_id,
                "input": form.model_dump(mode="json", exclude={"client_action_id"}),
            }
        )
        async with self.engine.connect() as db:
            old = await store.created_task(db, conversation_id, form.client_action_id)
        if old:
            await self.access.authorize(actor, old)
            return await self.create_receipt(old, actor, digest)
        if conversation.mode == "TECHNICIAN_ASSIST":
            if form.service_item_id or form.device_id or form.support_context is not None:
                raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        elif form.support_context is None:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        context = await self.access.context(
            actor,
            conversation,
            service_item_id=form.service_item_id,
            device_id=form.device_id,
            reported=form.support_context or SupportEnvironment(),
            new_execution=True,
        )
        async with self.engine.begin() as db:
            locked = await scope(db, conversation_id, lock=True)
            self.access.owner(actor, locked)
            old = await store.created_task(db, conversation_id, form.client_action_id)
            if old:
                if old.create_request_hash != digest:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                replay = await store.action_receipt(
                    db, old.id, actor.actor.owner_id, form.client_action_id, digest
                )
                if replay is None:
                    raise RuntimeError("任务创建缺少原回执")
                return replay.receipt
            await store.assert_idle(db, conversation_id)
            now = await store.decision_time(db)
            message_id = new_id()
            environment = SupportEnvironment.model_validate(
                MessageToDict(context.environment, preserving_proto_field_name=True)
            )
            fields: tuple[EnvironmentField, ...] = (
                "os_name",
                "os_version",
                "software_name",
                "software_version",
                "connection_type",
                "affected_scope",
                "error_code",
            )
            source_id = (
                conversation.ticket_id if conversation.mode == "TECHNICIAN_ASSIST" else message_id
            )
            if source_id is None:
                raise RuntimeError("任务环境缺少原始来源")
            reported = TaskEnvironment(
                **environment.model_dump(),
                field_sources={
                    field: FieldSource(
                        source_type="TICKET_SNAPSHOT"
                        if conversation.mode == "TECHNICIAN_ASSIST"
                        else "USER_REPORT",
                        source_id=source_id,
                        confirmed_at=now.replace(tzinfo=UTC),
                    )
                    for field in fields
                    if getattr(environment, field) not in {None, "UNKNOWN"}
                },
            )
            value = store.DiagnosisTask(
                id=new_id(),
                conversation_id=conversation_id,
                service_item_id=context.service_item_id or None,
                device_id=context.device_id or None,
                product_model_id=context.product_model_id or None,
                create_action_id=form.client_action_id,
                create_request_hash=digest,
                goal=form.goal,
                status="ACTIVE",
                task_state_version=0,
                diagnosis_context_version=context.diagnosis_context_version
                if conversation.mode == "TECHNICIAN_ASSIST"
                else 0,
                support_context=reported,
                hypothesis_state=HypothesisState(),
                ticket_event_sequence=context.ticket_event_sequence,
                procedure_document_id=None,
                procedure_sha256=None,
                budget_policy=self.budget.model_copy(deep=True),
                blocking_draft_root_id=None,
                blocked_reason=None,
                outcome_source=None,
                outcome_note=None,
                handover_ticket_id=None,
                closed_at=None,
                created_at=now,
                updated_at=now,
                row_version=0,
            )
            await store.insert_task(db, value)
            receipt = TaskReceipt(
                task_id=value.id,
                event_id=new_id(),
                task_state_version="1",
                status="ACTIVE",
                input_message_id=message_id,
                fact_id=new_id(),
            )
            await store.append_event(
                db,
                value.id,
                actor.actor.owner_id,
                form.client_action_id,
                digest,
                "TASK_CREATED",
                TaskEventDetails(receipt=receipt, reason="创建明确故障目标并保存原反馈"),
            )
            await self.record_manual_fact(
                db,
                actor,
                conversation,
                value,
                receipt,
                digest,
                form.client_action_id,
                form.goal,
                "SYMPTOM",
                "ASSERT",
                None,
                message_content=form.model_dump_json(exclude={"client_action_id"}),
            )
            await store.advance(
                db,
                value,
                status="ACTIVE",
                hypotheses=value.hypothesis_state,
                context=reported,
                context_version=value.diagnosis_context_version,
            )
            return receipt

    async def create_receipt(
        self, value: store.DiagnosisTask, actor: VerifiedIdentity, digest: str
    ) -> TaskReceipt:
        """原创建回执不被后续诊断版本替代，异义请求不返回旧目标。"""
        if value.create_request_hash != digest:
            raise BusinessError(CommonErrorCode.CONFLICT)
        async with self.engine.connect() as db:
            result = await store.action_receipt(
                db, value.id, actor.actor.owner_id, value.create_action_id, digest
            )
        if result is None:
            raise RuntimeError("任务创建缺少原回执")
        return result.receipt

    async def authorized(
        self, actor: VerifiedIdentity, identifier: str
    ) -> tuple[store.DiagnosisTask, ConversationScope, SupportContext]:
        """外部授权在事务前完成；事务内再次验证本地所有权和状态。"""
        async with self.engine.connect() as db:
            value = await store.task(db, identifier)
        conversation, context = await self.access.authorize(actor, value)
        return value, conversation, context

    async def locked(
        self, db: AsyncConnection, actor: VerifiedIdentity, old: store.DiagnosisTask
    ) -> store.DiagnosisTask:
        """固定锁序与本地归属复核，避免与归档、新Run或其他反馈穿插。"""
        conversation = await scope(db, old.conversation_id, lock=True)
        self.access.owner(actor, conversation)
        current = await store.task(db, old.id, lock=True)
        if current.conversation_id != conversation.id:
            raise BusinessError(CommonErrorCode.CONFLICT)
        return current

    async def mutable(
        self,
        db: AsyncConnection,
        value: store.DiagnosisTask,
        form: TaskAction,
        conversation: ConversationScope,
        context: SupportContext,
        *,
        synchronizing_context: bool = False,
    ) -> None:
        """新动作才检查版本/终态/环境及空闲；成功的原请求重放提前返回。"""
        if value.status in TERMINAL_STATUSES or value.task_state_version != int(
            form.task_state_version
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        if (
            not synchronizing_context
            and conversation.mode == "TECHNICIAN_ASSIST"
            and (
                not context.HasField("diagnosis_context_version")
                or context.diagnosis_context_version != value.diagnosis_context_version
                or (context.service_item_id or None) != value.service_item_id
                or (context.device_id or None) != value.device_id
            )
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        await store.assert_idle(db, value.conversation_id)

    async def change_fact(
        self, actor: VerifiedIdentity, identifier: str, form: TaskFactChange
    ) -> TaskReceipt:
        """更正链只追加；旧假设同事务失效，不把用户更正改成工具实测。"""
        original, conversation, context = await self.authorized(actor, identifier)
        digest = action_hash("FACT_CHANGED", identifier, form)
        async with self.engine.begin() as db:
            value = await self.locked(db, actor, original)
            replay = await store.action_receipt(
                db, value.id, actor.actor.owner_id, form.client_action_id, digest
            )
            if replay:
                return replay.receipt
            await self.mutable(db, value, form, conversation, context)
            if form.predecessor_fact_id:
                predecessor = await store.active_fact(db, value.id, form.predecessor_fact_id)
                if predecessor["fact_kind"] != form.fact_kind:
                    raise BusinessError(CommonErrorCode.INVALID_REQUEST)
            hypotheses = (
                invalidate_hypotheses(value.hypothesis_state, form.predecessor_fact_id)
                if form.predecessor_fact_id
                else value.hypothesis_state
            )
            withdrawn = (
                await store.withdraw_checks(db, value.id) if form.reported_conditions else []
            )
            status = (
                "ACTIVE"
                if form.reported_conditions and value.status == "WAITING_USER"
                else value.status
            )
            receipt = TaskReceipt(
                task_id=value.id,
                event_id=new_id(),
                task_state_version=str(value.task_state_version + 1),
                status=status,
                input_message_id=new_id(),
                fact_id=new_id(),
            )
            details = TaskEventDetails(
                receipt=receipt,
                reason="人工追加、更正或撤回事实",
                changed_check_ids=withdrawn,
                hypotheses_before=value.hypothesis_state
                if hypotheses != value.hypothesis_state
                else None,
                hypotheses_after=hypotheses if hypotheses != value.hypothesis_state else None,
            )
            await store.append_event(
                db,
                value.id,
                actor.actor.owner_id,
                form.client_action_id,
                digest,
                "FACT_CHANGED",
                details,
            )
            await self.record_manual_fact(
                db,
                actor,
                conversation,
                value,
                receipt,
                digest,
                form.client_action_id,
                form.statement,
                form.fact_kind,
                form.action,
                form.predecessor_fact_id,
                reported_conditions=tuple(form.reported_conditions),
            )
            await store.advance(
                db,
                value,
                status=status,
                hypotheses=hypotheses,
                context=value.support_context,
                context_version=value.diagnosis_context_version,
            )
            return receipt

    async def answer(
        self, actor: VerifiedIdentity, identifier: str, check_id: str, form: CheckAnswerInput
    ) -> TaskReceipt:
        """反馈先持久化，即使随后队列/额度不足也不丢失原答案。"""
        original, conversation, context = await self.authorized(actor, identifier)
        digest = action_hash("CHECK_ANSWERED", check_id, form)
        # 已保存的原回执可重读；新反馈必须在事务外重新验证原指引的传递来源。
        async with self.engine.connect() as db:
            replay = await store.action_receipt(
                db, identifier, actor.actor.owner_id, form.client_action_id, digest
            )
            if replay:
                return replay.receipt
            proposed = await store.check(db, identifier, check_id)
            proposal_run = await runs.run(db, proposed.proposed_run_id)
        if (
            proposal_run.task_id != identifier
            or proposal_run.conversation_id != conversation.id
            or proposal_run.owner_id != actor.actor.owner_id
        ):
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        await self.evidence.verify(proposal_run, actor.actor)
        async with self.engine.begin() as db:
            value = await self.locked(db, actor, original)
            replay = await store.action_receipt(
                db, value.id, actor.actor.owner_id, form.client_action_id, digest
            )
            if replay:
                return replay.receipt
            check = await store.check(db, value.id, check_id, lock=True)
            if check.requested_actor_id != actor.actor.owner_id or check.executor_type == "TOOL":
                raise BusinessError(CommonErrorCode.FORBIDDEN)
            try:
                form.answer.validate_for(check.check_kind, check.answer_schema)
            except (ValueError, OverflowError):
                raise BusinessError(CommonErrorCode.INVALID_REQUEST) from None
            answer = NormalizedAnswer(answer=form.answer, source_type=manual_source(conversation))
            if check.normalized_answer is not None:
                if answer != check.normalized_answer or check.answer_message_id is None:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                return (await store.answer_receipt(db, value.id, check.answer_message_id)).receipt
            await self.mutable(db, value, form, conversation, context)
            now = await store.decision_time(db)
            if (
                check.status != "OPEN"
                or not await store.check_context_valid(db, value, check)
                or check.expires_at is None
                or check.expires_at <= now
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
            remaining = await store.open_check_count(db, value.id)
            status: TaskStatus = (
                "ACTIVE" if value.status == "WAITING_USER" and remaining == 1 else value.status
            )
            receipt = TaskReceipt(
                task_id=value.id,
                event_id=new_id(),
                task_state_version=str(value.task_state_version + 1),
                status=status,
                input_message_id=new_id(),
                fact_id=new_id(),
                check_id=check.id,
            )
            await store.append_event(
                db,
                value.id,
                actor.actor.owner_id,
                form.client_action_id,
                digest,
                "CHECK_ANSWERED",
                TaskEventDetails(receipt=receipt, reason="收到人工反馈，不代表已执行或已恢复"),
            )
            message = form.answer.model_dump_json(exclude_none=True)
            statement = (
                str(form.answer.value)
                if check.check_kind == "CLARIFICATION"
                else (
                    f"人工检查反馈：execution_status={form.answer.execution_status}，outcome={form.answer.outcome}；完整规范值见原消息。"
                )
            )
            await self.record_manual_fact(
                db,
                actor,
                conversation,
                value,
                receipt,
                digest,
                form.client_action_id,
                statement,
                "ACTION_REPORT" if check.check_kind == "CHECK" else "OBSERVATION",
                "ASSERT",
                None,
                message_content=message,
            )
            if receipt.input_message_id is None or receipt.fact_id is None:
                raise RuntimeError("检查回执缺少原始来源")
            await store.answer_check(
                db,
                check,
                answer,
                receipt.input_message_id,
                receipt.fact_id,
                actor.actor.owner_id,
                now,
            )
            await store.advance(
                db,
                value,
                status=status,
                hypotheses=value.hypothesis_state,
                context=value.support_context,
                context_version=value.diagnosis_context_version,
            )
            return receipt

    async def record_manual_fact(
        self,
        db: AsyncConnection,
        actor: VerifiedIdentity,
        conversation: ConversationScope,
        value: store.DiagnosisTask,
        receipt: TaskReceipt,
        digest: str,
        key: str,
        statement: str,
        kind: str,
        operation: str,
        predecessor: str | None,
        *,
        message_content: str | None = None,
        reported_conditions: tuple[str, ...] = (),
    ) -> None:
        """人工事实与不可变原消息同任务，服务端决定来源；不接收任意工具标识。"""
        if receipt.input_message_id is None or receipt.fact_id is None:
            raise RuntimeError("人工事实缺少消息或结果身份")
        content = message_content or statement
        if reported_conditions:
            content += "\n明确报告的停止条件：" + "、".join(reported_conditions)
        message_key = hashlib.sha256(f"task:{value.id}:action:{key}".encode()).hexdigest()[:32]
        await store.append_message(
            db,
            identifier=receipt.input_message_id,
            task=value,
            content=content,
            content_sha256=hashlib.sha256(content.encode("utf-8")).hexdigest(),
            key=message_key,
            request_hash=digest,
        )
        await store.append_fact(
            db,
            identifier=receipt.fact_id,
            task=value,
            kind=kind,
            operation=operation,
            statement=statement,
            source_type=manual_source(conversation),
            actor_id=actor.actor.owner_id,
            message_id=receipt.input_message_id,
            predecessor=predecessor,
            reported_conditions=reported_conditions,
        )

    async def close(
        self,
        actor: VerifiedIdentity,
        identifier: str,
        form: TaskClose,
        status: Literal["CLOSED", "CANCELLED", "RESOLVED_REPORTED"],
    ) -> TaskReceipt:
        """终态不可重开；只表达人工原因或报告恢复，不写正式工单。"""
        original, conversation, context = await self.authorized(actor, identifier)
        digest = action_hash(status, identifier, form)
        async with self.engine.begin() as db:
            value = await self.locked(db, actor, original)
            replay = await store.action_receipt(
                db, value.id, actor.actor.owner_id, form.client_action_id, digest
            )
            if replay:
                return replay.receipt
            await self.mutable(db, value, form, conversation, context)
            changed = await store.withdraw_checks(db, value.id)
            await store.withdraw_drafts(db, value.id)
            now = await store.decision_time(db)
            receipt = TaskReceipt(
                task_id=value.id,
                event_id=new_id(),
                task_state_version=str(value.task_state_version + 1),
                status=status,
                input_message_id=new_id(),
                fact_id=new_id(),
            )
            await store.append_event(
                db,
                value.id,
                actor.actor.owner_id,
                form.client_action_id,
                digest,
                "TASK_CLOSED",
                TaskEventDetails(receipt=receipt, reason=form.reason, changed_check_ids=changed),
            )
            await self.record_manual_fact(
                db,
                actor,
                conversation,
                value,
                receipt,
                digest,
                form.client_action_id,
                form.reason,
                "OUTCOME",
                "ASSERT",
                None,
            )
            await store.advance(
                db,
                value,
                status=status,
                hypotheses=value.hypothesis_state,
                context=value.support_context,
                context_version=value.diagnosis_context_version,
                closed_at=now,
                outcome_source=manual_source(conversation),
                outcome_note=form.reason,
            )
            return receipt

    async def synchronize_context(
        self, actor: VerifiedIdentity, identifier: str, form: TaskContextChange
    ) -> TaskReceipt:
        """自助修改具名环境，技术仅同步ticket；原检查保留答案并按依赖判定复查。"""
        original, conversation, current_context = await self.authorized(actor, identifier)
        digest = action_hash("CONTEXT_SYNCED", identifier, form)
        async with self.engine.connect() as db:
            replay = await store.action_receipt(
                db, identifier, actor.actor.owner_id, form.client_action_id, digest
            )
        if replay:
            return replay.receipt
        technical = conversation.mode == "TECHNICIAN_ASSIST"
        if technical:
            if form.support_context is not None or "service_item_id" in form.model_fields_set:
                raise BusinessError(CommonErrorCode.INVALID_REQUEST)
            context = current_context
        else:
            if form.support_context is None:
                raise BusinessError(CommonErrorCode.INVALID_REQUEST)
            selected_service = original.service_item_id
            if "service_item_id" in form.model_fields_set:
                if (
                    original.service_item_id is not None
                    and form.service_item_id != original.service_item_id
                ):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                selected_service = form.service_item_id
            context = await self.access.context(
                actor,
                conversation,
                service_item_id=selected_service,
                device_id=original.device_id,
                reported=form.support_context,
                new_execution=True,
            )
        next_environment = SupportEnvironment.model_validate(
            MessageToDict(context.environment, preserving_proto_field_name=True)
        )
        async with self.engine.begin() as db:
            value = await self.locked(db, actor, original)
            replay = await store.action_receipt(
                db, identifier, actor.actor.owner_id, form.client_action_id, digest
            )
            if replay:
                return replay.receipt
            await self.mutable(db, value, form, conversation, context, synchronizing_context=True)
            fields: tuple[EnvironmentField, ...] = (
                "os_name",
                "os_version",
                "software_name",
                "software_version",
                "connection_type",
                "affected_scope",
                "error_code",
            )
            changed = [
                field
                for field in fields
                if getattr(next_environment, field) != getattr(value.support_context, field)
            ]
            scope_changed = (
                value.service_item_id,
                value.device_id,
                value.product_model_id,
            ) != (
                context.service_item_id or None,
                context.device_id or None,
                context.product_model_id or None,
            )
            version = (
                context.diagnosis_context_version
                if technical
                else value.diagnosis_context_version + bool(changed or scope_changed)
            )
            if version < value.diagnosis_context_version or (
                technical
                and (changed or scope_changed)
                and version == value.diagnosis_context_version
            ):
                raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
            now = await store.decision_time(db)
            message_id = new_id()
            source_id = conversation.ticket_id if technical else message_id
            if source_id is None:
                raise RuntimeError("环境同步缺少来源身份")
            sources = value.support_context.field_sources.copy()
            for field in changed:
                if getattr(next_environment, field) in {None, "UNKNOWN"}:
                    sources.pop(field, None)
                else:
                    sources[field] = FieldSource(
                        source_type="TICKET_SNAPSHOT" if technical else "USER_REPORT",
                        source_id=source_id,
                        confirmed_at=now.replace(tzinfo=UTC),
                    )
            next_context = TaskEnvironment(**next_environment.model_dump(), field_sources=sources)
            change = ContextChange(
                previous_version=value.diagnosis_context_version,
                current_version=version,
                changed_fields=changed,
                scope_changed=scope_changed,
                previous_service_item_id=value.service_item_id,
                current_service_item_id=context.service_item_id or None,
                previous_device_id=value.device_id,
                current_device_id=context.device_id or None,
                previous_product_model_id=value.product_model_id,
                current_product_model_id=context.product_model_id or None,
            )
            invalidated = await store.invalidate_checks(db, value, change)
            hypotheses = (
                invalidate_hypotheses(value.hypothesis_state)
                if version != value.diagnosis_context_version
                else value.hypothesis_state
            )
            status: TaskStatus = value.status
            if status == "BLOCKED_EXTERNAL" or (
                status == "WAITING_USER" and await store.open_check_count(db, value.id) == 0
            ):
                status = "ACTIVE"
            receipt = TaskReceipt(
                task_id=value.id,
                event_id=new_id(),
                task_state_version=str(value.task_state_version + 1),
                status=status,
                input_message_id=message_id,
                fact_id=new_id(),
            )
            await store.append_event(
                db,
                value.id,
                actor.actor.owner_id,
                form.client_action_id,
                digest,
                "CONTEXT_SYNCED",
                TaskEventDetails(
                    receipt=receipt,
                    reason="显式同步诊断环境，相关检查需复查",
                    changed_check_ids=invalidated,
                    hypotheses_before=value.hypothesis_state
                    if hypotheses != value.hypothesis_state
                    else None,
                    hypotheses_after=hypotheses if hypotheses != value.hypothesis_state else None,
                    context_change=change,
                ),
            )
            await self.record_manual_fact(
                db,
                actor,
                conversation,
                replace(value, support_context=next_context, diagnosis_context_version=version),
                receipt,
                digest,
                form.client_action_id,
                "显式同步诊断环境，具名字段及来源见原消息",
                "ENVIRONMENT",
                "ASSERT",
                None,
                message_content=next_context.model_dump_json(),
            )
            if technical or scope_changed:
                await store.synchronize_scope(
                    db,
                    value,
                    context.service_item_id or None,
                    context.device_id or None,
                    context.product_model_id or None,
                    context.ticket_event_sequence,
                )
            await store.withdraw_drafts(db, value.id)
            await store.advance(
                db,
                value,
                status=status,
                hypotheses=hypotheses,
                context=next_context,
                context_version=version,
            )
            return receipt
