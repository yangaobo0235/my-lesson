"""人工动作草稿的模板生成与当前来源复核，不写正式工单或消费结果。作者：yangaobo。"""

from __future__ import annotations

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.ai.v1.execution_pb2 import ValidatedDraft, ValidateDraftRequest
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.draft import DraftErrorCode
from servicedesk_ai.repositories import diagnosis as store
from servicedesk_ai.repositories import drafts as queries
from servicedesk_ai.schemas.diagnosis import DraftCreate
from servicedesk_ai.schemas.diagnosis_views import DraftContent, DraftView
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.services.conversations import ConversationService, instant


class DraftService:
    """固定成功源运行、诊断版本、工单版本与24小时确认期限。"""

    def __init__(self, engine: AsyncEngine, conversations: ConversationService) -> None:
        """沿已有会话和来源用例，不创建另一条模型或工单执行路径。"""
        self.engine, self.conversations = engine, conversations

    async def get(
        self, actor: VerifiedIdentity, identifier: str, *, validate: bool = False
    ) -> DraftView:
        """查询原草稿保留过期回执；实际采用时才强制期限及当前事实版本。"""
        async with self.engine.connect() as db:
            row = await queries.read_draft(db, {"id": identifier})
            now = await queries.database_now(db)
        if row is None or row["owner_id"] != actor.actor.owner_id:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        conversation = await self.conversations.authorized(actor, row["conversation_id"])
        run = await self.conversations.get_generation(actor, row["source_generation_id"])
        if run.status != "SUCCEEDED" or run.sources_hidden:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        if validate and row["expires_at"] <= now:
            raise BusinessError(DraftErrorCode.DRAFT_EXPIRED)
        if validate and row["state_version"] != conversation.state_version:
            raise BusinessError(CommonErrorCode.CONFLICT)
        return DraftView(
            id=row["id"],
            source_generation_id=row["source_generation_id"],
            purpose=row["purpose"],
            ticket_id=row["ticket_id"],
            ticket_version=None if row["ticket_version"] is None else str(row["ticket_version"]),
            state_version=str(row["state_version"]),
            content=DraftContent.model_validate_json(row["content"]),
            expires_at=instant(row["expires_at"]),
        )

    async def by_request(self, actor: VerifiedIdentity, key: str) -> DraftView:
        """拥有者原请求键查询，未见不推断在途动作已经失败。"""
        async with self.engine.connect() as db:
            identifier = await queries.find_draft_request(
                db, {"owner": actor.actor.owner_id, "key": key}
            )
        if identifier is None:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        return await self.get(actor, identifier)

    async def create(
        self, actor: VerifiedIdentity, identifier: str, form: DraftCreate
    ) -> DraftView:
        """客户只取本人明确确认反馈，技术用途只取源运行已校验结构化建议。"""
        request_hash = canonical_hash({"conversation_id": identifier, **form.model_dump()})
        async with self.engine.connect() as db:
            old = await queries.draft_request_receipt(
                db, {"owner": actor.actor.owner_id, "key": form.client_request_id}
            )
        if old:
            if old[1] != request_hash:
                raise BusinessError(CommonErrorCode.CONFLICT)
            return await self.get(actor, old[0])
        conversation = await self.conversations.authorized(actor, identifier)
        output = await self.conversations.get_generation(actor, form.source_generation_id)
        if (
            output.conversation_id != identifier
            or output.status != "SUCCEEDED"
            or output.sources_hidden
            or output.output_state_version != str(conversation.state_version)
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        content = DraftContent()
        if form.purpose == "CREATE_TICKET":
            if conversation.mode != "CUSTOMER_SELF_SERVICE" or not form.confirmed_fact_ids:
                raise BusinessError(CommonErrorCode.INVALID_REQUEST)
            async with self.engine.connect() as db:
                confirmed = []
                for fact_id in dict.fromkeys(form.confirmed_fact_ids):
                    row = await queries.confirmed_user_fact(
                        db,
                        {
                            "fact": fact_id,
                            "conversation": identifier,
                            "owner": actor.actor.owner_id,
                        },
                    )
                    if row is None:
                        raise BusinessError(CommonErrorCode.CONFLICT)
                    confirmed.append(row)
            content = DraftContent(
                title=conversation.title,
                symptom="\n".join(row[1] for row in confirmed if row[0] == "OBSERVATION")[:5000],
                attempted_actions="\n".join(
                    row[1] for row in confirmed if row[0] == "PERFORMED_ACTION"
                )[:5000],
            )
        else:
            if conversation.mode != "TECHNICIAN_ASSIST" or output.structured_result is None:
                raise BusinessError(CommonErrorCode.INVALID_REQUEST)
            source = output.structured_result
            body = (
                source.proposed_record
                if form.purpose == "ADD_RECORD"
                else source.proposed_resolution
            )
            if not body:
                raise BusinessError(CommonErrorCode.CONFLICT)
            content = DraftContent(
                content=body,
                resolution_type=source.resolution_type
                if form.purpose == "RESOLVE_TICKET"
                else None,
                referral_target=source.referral_target
                if form.purpose == "RESOLVE_TICKET"
                else None,
            )
        draft_id = store.new_id()
        try:
            async with self.engine.begin() as db:
                current = await store.conversation(db, identifier, lock=True)
                await store.require_idle(db, identifier)
                if current.state_version != conversation.state_version:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                await queries.insert_draft(
                    db,
                    {
                        "id": draft_id,
                        "owner": actor.actor.owner_id,
                        "conversation": identifier,
                        "run": form.source_generation_id,
                        "purpose": form.purpose,
                        "ticket": conversation.ticket_id,
                        "ticket_version": None
                        if output.ticket_version is None
                        else int(output.ticket_version),
                        "version": conversation.state_version,
                        "key": form.client_request_id,
                        "hash": request_hash,
                        "content": content.model_dump_json(),
                    },
                )
        except IntegrityError:
            async with self.engine.connect() as db:
                old = await queries.draft_request_receipt(
                    db, {"owner": actor.actor.owner_id, "key": form.client_request_id}
                )
            if old is None or old[1] != request_hash:
                raise BusinessError(CommonErrorCode.CONFLICT) from None
            draft_id = old[0]
        return await self.get(actor, draft_id)

    async def validate(self, request: ValidateDraftRequest) -> ValidatedDraft:
        """ticket重新核验拥有者、用途、精确目标、期限、版本及全部来源。"""
        actor = await self.conversations.rpc.identity.verify_ref(request.actor)
        draft = await self.get(actor, request.draft_id, validate=True)
        async with self.engine.connect() as db:
            identifier = await queries.draft_conversation(db, {"id": draft.id})
        conversation = await self.conversations.authorized(actor, identifier)
        if draft.purpose != request.purpose or conversation.customer_id != request.customer_id:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        if draft.purpose == "CREATE_TICKET":
            if (
                request.HasField("ticket_id")
                or request.HasField("ticket_version")
                or request.service_item_id != conversation.service_item_id
                or (request.device_id or None) != conversation.device_id
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
        elif (
            request.ticket_id != draft.ticket_id
            or not request.HasField("ticket_version")
            or str(request.ticket_version) != draft.ticket_version
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        content = draft.content
        result = ValidatedDraft(
            id=draft.id,
            source_generation_id=draft.source_generation_id,
            purpose=draft.purpose,
            title=content.title,
            symptom=content.symptom,
            attempted_actions=content.attempted_actions,
            content=content.content,
        )
        if content.resolution_type:
            result.resolution_type = content.resolution_type
        if content.referral_target:
            result.referral_target = content.referral_target
        return result
