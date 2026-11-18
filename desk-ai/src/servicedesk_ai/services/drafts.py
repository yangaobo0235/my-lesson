"""任务范围的不可变人工动作草稿；正式采用事实仅归ticket回执。作者：yangaobo。"""

from __future__ import annotations

import json
from datetime import UTC, timedelta

import grpc
from sqlalchemy.engine import RowMapping
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.ai.v1.execution_pb2 import ValidatedDraft, ValidateDraftRequest
from desk_contracts.ticket.v1.support_pb2 import (
    ContextRequest,
    DraftReceipt,
    DraftReceiptRequest,
    SupportContext,
)
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.draft import DraftErrorCode
from servicedesk_ai.infrastructure.identity import translate_failure
from servicedesk_ai.repositories import drafts as queries
from servicedesk_ai.repositories import tasks
from servicedesk_ai.repositories.conversation_scope import scope
from servicedesk_ai.repositories.values import json_value, new_id
from servicedesk_ai.schemas.diagnosis import DraftCreate, DraftRevision, DraftWithdraw
from servicedesk_ai.schemas.diagnosis_views import DraftContent, DraftView
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.schemas.tasks import TaskAction, TaskEventDetails, TaskReceipt
from servicedesk_ai.services.conversations import instant
from servicedesk_ai.services.run_reads import RunReads
from servicedesk_ai.services.task_access import TaskAccess


class DraftService:
    """草稿固定任务、环境、来源和业务版本，编辑不延长根期限。"""

    def __init__(self, engine: AsyncEngine, access: TaskAccess, run_reads: RunReads) -> None:
        """无工单写入和模型客户端，只构造可人工复核的提议。"""
        self.engine, self.access, self.run_reads = engine, access, run_reads

    async def booking_context(
        self, actor: VerifiedIdentity, task: tasks.DiagnosisTask
    ) -> SupportContext:
        """预约仅读取已核对建单回执的目标，终态任务不因此重新开放排障。"""
        conversation = await self.access.conversation(actor, task.conversation_id)
        if (
            conversation.mode != "CUSTOMER_SELF_SERVICE"
            or task.status != "HANDED_OFF"
            or task.handover_ticket_id is None
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        try:
            value = await self.access.rpc.ticket.CheckContext(
                ContextRequest(
                    actor=actor.actor, purpose="HISTORY", ticket_id=task.handover_ticket_id
                ),
                **self.access.rpc.options(),
            )
        except grpc.aio.AioRpcError as error:
            raise translate_failure(error) from None
        if (
            value.customer_id != conversation.customer_id
            or value.ticket_id != task.handover_ticket_id
            or not value.HasField("ticket_row_version")
        ):
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
        return value

    async def get(
        self, actor: VerifiedIdentity, identifier: str, *, validate: bool = False
    ) -> DraftView:
        """读取和采用共用来源授权，采用另核当前修订、期限及任务环境版本。"""
        async with self.engine.connect() as db:
            row = await queries.read_draft(db, {"id": identifier})
            now = await queries.database_now(db)
            if row is None or row["owner_id"] != actor.actor.owner_id:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            task = await tasks.task(db, row["task_id"])
        _, context = await self.access.authorize(actor, task)
        if row["purpose"] == "BOOK_ONSITE":
            context = await self.booking_context(actor, task)
        if row["source_run_id"]:
            output = await self.run_reads.get(actor, row["source_run_id"], require_sources=True)
            if output.status != "SUCCEEDED" or output.sources_hidden:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
            if validate and output.stale:
                raise BusinessError(CommonErrorCode.CONFLICT)
        if validate and row["expires_at"] <= now:
            raise BusinessError(DraftErrorCode.DRAFT_EXPIRED)
        if validate and (
            row["status"] != "PENDING"
            or task.task_state_version != row["task_state_version"]
            or task.diagnosis_context_version != row["diagnosis_context_version"]
            or (task.closed_at is not None and row["purpose"] != "BOOK_ONSITE")
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        if (
            validate
            and row["ticket_id"]
            and (
                context.ticket_row_version != row["ticket_row_version"]
                or (
                    row["purpose"] != "BOOK_ONSITE"
                    and context.diagnosis_context_version != row["diagnosis_context_version"]
                )
            )
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        refs = json.loads(row["source_refs"])
        async with self.engine.connect() as db:
            for fact_id in refs["fact_ids"]:
                await tasks.active_fact(db, task.id, fact_id)
        content = DraftContent.model_validate_json(row["content"])
        if canonical_hash(content.model_dump(mode="json")) != row["content_sha256"]:
            raise BusinessError(CommonErrorCode.CONFLICT)
        return DraftView(
            id=row["id"],
            task_id=task.id,
            draft_root_id=row["draft_root_id"],
            revision_number=row["revision_number"],
            content_sha256=row["content_sha256"],
            status="EXPIRED"
            if row["status"] == "PENDING" and row["expires_at"] <= now
            else row["status"],
            source_run_id=row["source_run_id"],
            purpose=row["purpose"],
            ticket_id=row["ticket_id"],
            ticket_row_version=None
            if row["ticket_row_version"] is None
            else str(row["ticket_row_version"]),
            task_state_version=str(row["task_state_version"]),
            diagnosis_context_version=str(row["diagnosis_context_version"]),
            content=content,
            expires_at=instant(row["expires_at"]),
        )

    async def by_request(self, actor: VerifiedIdentity, key: str) -> DraftView:
        """按拥有者原键查询修订，响应未知不能改键重建。"""
        async with self.engine.connect() as db:
            identifier = await queries.find_draft_request(
                db, {"owner": actor.actor.owner_id, "key": key}
            )
        if identifier is None:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        return await self.get(actor, identifier)

    async def replay(self, actor: VerifiedIdentity, key: str, digest: str) -> DraftView | None:
        """回执优先于当前版本、有效期和来源运行可执行状态。"""
        async with self.engine.connect() as db:
            old = await queries.draft_request_receipt(
                db, {"owner": actor.actor.owner_id, "key": key}
            )
        if old is None:
            return None
        if old[1] != digest:
            raise BusinessError(CommonErrorCode.CONFLICT)
        return await self.get(actor, old[0])

    async def create(
        self, actor: VerifiedIdentity, identifier: str, form: DraftCreate
    ) -> DraftView:
        """按任务创建；自助提单可使用明确确认事实，无需额外模型调用。"""
        digest = canonical_hash({"task": identifier, **form.model_dump(mode="json")})
        old = await self.replay(actor, form.client_request_id, digest)
        if old:
            return old
        async with self.engine.connect() as db:
            task = await tasks.task(db, identifier)
        conversation, context = await self.access.authorize(actor, task)
        output = (
            await self.run_reads.get(actor, form.source_run_id, require_sources=True)
            if form.source_run_id
            else None
        )
        if output and (
            output.task_id != task.id
            or output.status != "SUCCEEDED"
            or output.sources_hidden
            or output.stale
            or output.output_state_version != str(task.task_state_version)
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        if form.purpose == "BOOK_ONSITE":
            context = await self.booking_context(actor, task)
            content = DraftContent(slot_id=form.slot_id)
        elif form.purpose == "CREATE_TICKET":
            if conversation.mode != "CUSTOMER_SELF_SERVICE" or not form.confirmed_fact_ids:
                raise BusinessError(CommonErrorCode.INVALID_REQUEST)
            confirmed = []
            async with self.engine.connect() as db:
                for fact_id in dict.fromkeys(form.confirmed_fact_ids):
                    fact = await queries.confirmed_user_fact(
                        db, {"fact": fact_id, "task": task.id, "owner": actor.actor.owner_id}
                    )
                    if fact is None:
                        raise BusinessError(CommonErrorCode.CONFLICT)
                    confirmed.append(fact)
            content = DraftContent(
                title=task.goal[:100],
                symptom="\n".join(
                    row[1] for row in confirmed if row[0] in {"SYMPTOM", "OBSERVATION"}
                )[:5000],
                attempted_actions="\n".join(
                    row[1] for row in confirmed if row[0] == "ACTION_REPORT"
                )[:5000],
            )
        else:
            if (
                conversation.mode != "TECHNICIAN_ASSIST"
                or output is None
                or output.structured_result is None
            ):
                raise BusinessError(CommonErrorCode.INVALID_REQUEST)
            result = output.structured_result
            content = DraftContent(
                content=(
                    result.proposed_record
                    if form.purpose == "ADD_RECORD"
                    else "\n".join(
                        item.instruction
                        for item in result.checks
                        if item.check_kind == "CLARIFICATION"
                    )
                    if form.purpose == "REQUEST_INFORMATION"
                    else result.proposed_resolution
                )
                or "",
                resolution_type=result.resolution_type
                if form.purpose == "RESOLVE_TICKET"
                else None,
                referral_target=result.referral_target
                if form.purpose == "RESOLVE_TICKET"
                else None,
                follow_up_at=form.follow_up_at,
            )
        self.validate_content(form.purpose, content)
        draft_id = new_id()
        try:
            async with self.engine.begin() as db:
                await scope(db, task.conversation_id, lock=True)
                current = await tasks.task(db, task.id, lock=True)
                await tasks.assert_idle(db, task.conversation_id)
                if (
                    current.task_state_version != task.task_state_version
                    or (current.closed_at is not None and form.purpose != "BOOK_ONSITE")
                    or current.blocking_draft_root_id is not None
                ):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                for fact_id in form.confirmed_fact_ids:
                    await tasks.active_fact(db, task.id, fact_id)
                await queries.insert_draft(
                    db,
                    {
                        "id": draft_id,
                        "owner": actor.actor.owner_id,
                        "conversation": task.conversation_id,
                        "task": task.id,
                        "context": task.diagnosis_context_version,
                        "run": form.source_run_id,
                        "purpose": form.purpose,
                        "ticket": task.handover_ticket_id
                        if form.purpose == "BOOK_ONSITE"
                        else conversation.ticket_id,
                        "ticket_row_version": context.ticket_row_version
                        if conversation.ticket_id or form.purpose == "BOOK_ONSITE"
                        else None,
                        "version": task.task_state_version + int(form.wait_for_confirmation),
                        "key": form.client_request_id,
                        "hash": digest,
                        "content": content.model_dump_json(),
                        "root": draft_id,
                        "revision": 1,
                        "previous": None,
                        "content_hash": canonical_hash(content.model_dump(mode="json")),
                        "expires_at": None,
                        "refs": json_value(
                            {
                                "schema_version": 1,
                                "fact_ids": form.confirmed_fact_ids,
                                "source_usage_ids": [],
                            }
                        ),
                    },
                )
                if form.wait_for_confirmation:
                    details = TaskEventDetails(
                        receipt=TaskReceipt(
                            task_id=task.id,
                            event_id=new_id(),
                            task_state_version=str(task.task_state_version + 1),
                            status="WAITING_APPROVAL",
                        ),
                        reason="人工选择等待此草稿确认，后续排障需先核对正式回执",
                        draft_root_id=draft_id,
                    )
                    await tasks.append_event(
                        db,
                        task.id,
                        actor.actor.owner_id,
                        form.client_request_id,
                        digest,
                        "DRAFT_WAITING",
                        details,
                    )
                    await tasks.advance(
                        db,
                        current,
                        status="WAITING_APPROVAL",
                        hypotheses=current.hypothesis_state,
                        context=current.support_context,
                        context_version=current.diagnosis_context_version,
                        blocking_draft_root_id=draft_id,
                    )
        except IntegrityError:
            old = await self.replay(actor, form.client_request_id, digest)
            if old is None:
                raise BusinessError(CommonErrorCode.CONFLICT) from None
            return old
        return await self.get(actor, draft_id)

    async def revise(
        self, actor: VerifiedIdentity, identifier: str, form: DraftRevision
    ) -> DraftView:
        """并发修订只允许一个后继；原键先查，根期限和固定来源不变。"""
        digest = canonical_hash({"previous": identifier, **form.model_dump(mode="json")})
        old = await self.replay(actor, form.client_request_id, digest)
        if old:
            return old
        try:
            original = await self.get(actor, identifier, validate=True)
            if original.content_sha256 != form.content_sha256:
                raise BusinessError(CommonErrorCode.CONFLICT)
            self.validate_content(original.purpose, form.content)
            next_id = new_id()
            async with self.engine.begin() as db:
                conversation_id = await queries.draft_conversation(db, {"id": identifier})
                await scope(db, conversation_id, lock=True)
                task = await tasks.task(db, original.task_id, lock=True)
                await tasks.assert_idle(db, conversation_id)
                await queries.lock_draft(db, original.draft_root_id)
                row = await queries.read_draft(db, {"id": identifier})
                now = await queries.database_now(db)
                if (
                    row is None
                    or row["status"] != "PENDING"
                    or row["expires_at"] <= now
                    or str(task.task_state_version) != original.task_state_version
                ):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                await queries.insert_draft(
                    db,
                    {
                        "id": next_id,
                        "owner": actor.actor.owner_id,
                        "conversation": conversation_id,
                        "task": task.id,
                        "context": row["diagnosis_context_version"],
                        "refs": row["source_refs"],
                        "run": original.source_run_id,
                        "purpose": original.purpose,
                        "ticket": original.ticket_id,
                        "ticket_row_version": row["ticket_row_version"],
                        "version": row["task_state_version"],
                        "key": form.client_request_id,
                        "hash": digest,
                        "content": form.content.model_dump_json(),
                        "root": original.draft_root_id,
                        "revision": original.revision_number + 1,
                        "previous": identifier,
                        "content_hash": canonical_hash(form.content.model_dump(mode="json")),
                        "expires_at": row["expires_at"],
                    },
                )
                await queries.set_status(db, identifier, "SUPERSEDED")
        except (IntegrityError, BusinessError):
            old = await self.replay(actor, form.client_request_id, digest)
            if old:
                return old
            raise
        return await self.get(actor, next_id)

    async def withdraw(
        self, actor: VerifiedIdentity, identifier: str, form: DraftWithdraw
    ) -> DraftView:
        """撤回只阻止新校验，在途正式动作仍以ticket回执为准。"""
        original = await self.get(actor, identifier)
        if original.content_sha256 != form.content_sha256:
            raise BusinessError(CommonErrorCode.CONFLICT)
        async with self.engine.begin() as db:
            conversation_id = await queries.draft_conversation(db, {"id": identifier})
            await scope(db, conversation_id, lock=True)
            task = await tasks.task(db, original.task_id, lock=True)
            await queries.lock_draft(db, original.draft_root_id)
            row = await queries.read_draft(db, {"id": identifier})
            if row is None or row["status"] not in {"PENDING", "WITHDRAWN"}:
                raise BusinessError(CommonErrorCode.CONFLICT)
            if row["status"] == "PENDING" and task.blocking_draft_root_id == original.draft_root_id:
                await tasks.assert_idle(db, conversation_id)
                details = TaskEventDetails(
                    receipt=TaskReceipt(
                        task_id=task.id,
                        event_id=new_id(),
                        task_state_version=str(task.task_state_version + 1),
                        status=task.status,
                    ),
                    draft_root_id=original.draft_root_id,
                    reason="用户撤回阻断草稿；等待核对Java正式回执，不推断在途动作失败",
                )
                digest = canonical_hash({"withdraw": identifier, "content": form.content_sha256})
                await tasks.append_event(
                    db,
                    task.id,
                    actor.actor.owner_id,
                    digest[:32],
                    digest,
                    "DRAFT_WITHDRAWN",
                    details,
                )
                await tasks.advance(
                    db,
                    task,
                    status=task.status,
                    hypotheses=task.hypothesis_state,
                    context=task.support_context,
                    context_version=task.diagnosis_context_version,
                )
            await queries.set_status(db, identifier, "WITHDRAWN")
        return await self.get(actor, identifier)

    async def validate(self, request: ValidateDraftRequest) -> ValidatedDraft:
        """ticket执行前核对精确目标和修订，返回最长30秒内部校验证明。"""
        actor = await self.access.rpc.identity.verify_ref(request.actor)
        draft = await self.get(actor, request.draft_id, validate=True)
        async with self.engine.connect() as db:
            task = await tasks.task(db, draft.task_id)
        conversation, _ = await self.access.authorize(actor, task)
        if draft.purpose != request.purpose or conversation.customer_id != request.customer_id:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        if draft.purpose == "CREATE_TICKET":
            invalid = (
                request.HasField("ticket_id")
                or request.HasField("ticket_row_version")
                or request.service_item_id != task.service_item_id
                or (request.device_id or None) != task.device_id
            )
        else:
            invalid = (
                request.ticket_id != draft.ticket_id
                or not request.HasField("ticket_row_version")
                or str(request.ticket_row_version) != draft.ticket_row_version
            )
        if invalid or request.content_sha256 != draft.content_sha256:
            raise BusinessError(CommonErrorCode.CONFLICT)
        async with self.engine.begin() as db:
            await scope(db, task.conversation_id, lock=True)
            current = await tasks.task(db, task.id, lock=True)
            await tasks.assert_idle(db, task.conversation_id)
            await queries.lock_draft(db, draft.draft_root_id)
            row = await queries.read_draft(db, {"id": draft.id})
            now = await queries.database_now(db)
            if (
                row is None
                or row["status"] != "PENDING"
                or row["expires_at"] <= now
                or str(current.task_state_version) != draft.task_state_version
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
        result = ValidatedDraft(
            id=draft.id,
            task_id=draft.task_id,
            task_state_version=int(draft.task_state_version),
            diagnosis_context_version=int(draft.diagnosis_context_version),
            purpose=draft.purpose,
            draft_root_id=draft.draft_root_id,
            content_sha256=draft.content_sha256,
            validation_id=new_id(),
            validated_at_epoch_ms=int(now.replace(tzinfo=UTC).timestamp() * 1000),
            valid_until_epoch_ms=int(
                min(now + timedelta(seconds=30), row["expires_at"]).replace(tzinfo=UTC).timestamp()
                * 1000
            ),
            title=draft.content.title,
            symptom=draft.content.symptom,
            attempted_actions=draft.content.attempted_actions,
            content=draft.content.content,
        )
        if draft.source_run_id:
            result.source_run_id = draft.source_run_id
        if draft.content.resolution_type:
            result.resolution_type = draft.content.resolution_type
        if draft.content.referral_target:
            result.referral_target = draft.content.referral_target
        if draft.content.follow_up_at:
            result.follow_up_at = draft.content.follow_up_at.isoformat()
        if draft.content.slot_id:
            result.slot_id = draft.content.slot_id
        return result

    async def synchronize(
        self, actor: VerifiedIdentity, identifier: str, form: TaskAction
    ) -> TaskReceipt:
        """显式核对Java正式回执，不以浏览器或模型声称成功推进任务。"""
        digest = canonical_hash({"draft": identifier, **form.model_dump(mode="json")})
        async with self.engine.connect() as db:
            row = await queries.read_draft(db, {"id": identifier})
            if row is None or row["owner_id"] != actor.actor.owner_id:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
        await self.access.conversation(actor, row["conversation_id"])
        async with self.engine.connect() as db:
            old = await tasks.action_receipt(
                db, row["task_id"], actor.actor.owner_id, form.client_action_id, digest
            )
        if old:
            return old.receipt
        try:
            official = await self.access.rpc.ticket.ReadDraftReceipt(
                DraftReceiptRequest(actor=actor.actor, draft_root_id=row["draft_root_id"]),
                **self.access.rpc.options(),
            )
        except grpc.aio.AioRpcError as error:
            raise translate_failure(error) from None
        return await self.apply_receipt(row, official, actor=actor, form=form)

    async def apply_receipt(
        self,
        row: RowMapping,
        official: DraftReceipt,
        *,
        actor: VerifiedIdentity | None = None,
        form: TaskAction | None = None,
    ) -> TaskReceipt:
        """用户同步与服务维护共享正式回执提交；系统对账不伪造会话或用户动作。"""
        digest = (
            canonical_hash({"draft": row["id"], **form.model_dump(mode="json")}) if form else None
        )
        if (
            official.draft_root_id != row["draft_root_id"]
            or official.action_type != row["purpose"]
            or (row["ticket_id"] is not None and official.ticket_id != row["ticket_id"])
        ):
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
        async with self.engine.begin() as db:
            await scope(db, row["conversation_id"], lock=True)
            task = await tasks.task(db, row["task_id"], lock=True)
            await tasks.assert_idle(db, row["conversation_id"])
            if actor is not None and form is not None and digest is not None:
                old = await tasks.action_receipt(
                    db, task.id, actor.actor.owner_id, form.client_action_id, digest
                )
                if old:
                    return old.receipt
            synchronized = await queries.synchronized_receipt(db, task.id, official.receipt_id)
            if synchronized:
                return synchronized.receipt
            adopted = await queries.read_draft(db, {"id": official.draft_id})
            if (
                adopted is None
                or adopted["draft_root_id"] != row["draft_root_id"]
                or adopted["task_id"] != task.id
            ):
                raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
            if form is not None and str(task.task_state_version) != form.task_state_version:
                raise BusinessError(CommonErrorCode.CONFLICT)
            now = await tasks.decision_time(db)
            status = task.status
            closed, source, note = task.closed_at, task.outcome_source, task.outcome_note
            handover = task.handover_ticket_id
            if row["purpose"] == "CREATE_TICKET":
                if official.result_code != "APPLIED" or (
                    handover is not None and handover != official.ticket_id
                ):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                if task.closed_at is None:
                    status, closed, source = "HANDED_OFF", now, "SYSTEM_RULE"
                    note = "已核对Java建单回执，正式工单由人工服务继续处理"
                handover = official.ticket_id
                await tasks.withdraw_drafts(db, task.id)
                await tasks.withdraw_checks(db, task.id)
            elif (
                task.status == "WAITING_APPROVAL"
                and task.blocking_draft_root_id == row["draft_root_id"]
            ):
                status = "WAITING_USER" if await tasks.open_check_count(db, task.id) else "ACTIVE"
            receipt = TaskReceipt(
                task_id=task.id,
                event_id=new_id(),
                task_state_version=str(task.task_state_version + 1),
                status=status,
            )
            details = TaskEventDetails(
                receipt=receipt,
                reason="已核对Java正式动作回执：" + official.result_code,
                draft_root_id=row["draft_root_id"],
                action_receipt_id=official.receipt_id,
            )
            if actor is not None and form is not None and digest is not None:
                await tasks.append_event(
                    db,
                    task.id,
                    actor.actor.owner_id,
                    form.client_action_id,
                    digest,
                    "DRAFT_CONFIRMED",
                    details,
                )
            else:
                await tasks.append_system_event(db, "DRAFT_CONFIRMED", details)
            await tasks.advance(
                db,
                task,
                status=status,
                hypotheses=task.hypothesis_state,
                context=task.support_context,
                context_version=task.diagnosis_context_version,
                closed_at=closed,
                outcome_source=source,
                outcome_note=note,
                handover_ticket_id=handover,
            )
            return receipt

    @staticmethod
    def validate_content(purpose: str, content: DraftContent) -> None:
        """确认字段遵守对应Java动作的长度和用途，不能夹带无关内容。"""
        if purpose != "REQUEST_INFORMATION" and content.follow_up_at is not None:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        if purpose != "BOOK_ONSITE" and content.slot_id is not None:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        if purpose == "BOOK_ONSITE":
            valid = bool(content.slot_id) and not any(
                (
                    content.title,
                    content.symptom,
                    content.attempted_actions,
                    content.content,
                    content.resolution_type,
                    content.referral_target,
                )
            )
        elif purpose == "CREATE_TICKET":
            valid = (
                bool(content.title.strip() and content.symptom.strip())
                and len(content.title) <= 100
                and len(content.symptom) <= 5000
                and len(content.attempted_actions) <= 5000
                and not content.content
                and content.resolution_type is None
                and content.referral_target is None
            )
        else:
            valid = (
                bool(content.content.strip())
                and len(content.content) <= 5000
                and not content.title
                and not content.symptom
                and not content.attempted_actions
            )
            if purpose in {"ADD_RECORD", "REQUEST_INFORMATION"}:
                valid = (
                    valid and content.resolution_type is None and content.referral_target is None
                )
                if purpose == "REQUEST_INFORMATION":
                    valid = (
                        valid and len(content.content) <= 1000 and content.follow_up_at is not None
                    )
            else:
                valid = (
                    valid
                    and content.resolution_type is not None
                    and (content.referral_target is None or len(content.referral_target) <= 300)
                    and (
                        content.resolution_type != "REFERRED"
                        or bool(content.referral_target and content.referral_target.strip())
                    )
                )
        if not valid:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
