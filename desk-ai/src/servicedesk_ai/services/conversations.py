"""三种私有会话的幂等创建、管理与归档；不维护诊断任务环境。作者：yangaobo。"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import overload

import grpc
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.ticket.v1.support_pb2 import TicketRequest
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.infrastructure.identity import translate_failure
from servicedesk_ai.repositories import runs
from servicedesk_ai.repositories.conversation_scope import ConversationScope, scope
from servicedesk_ai.repositories.values import new_id
from servicedesk_ai.schemas.diagnosis import (
    ConversationChange,
    ConversationCreate,
    SupportEnvironment,
)
from servicedesk_ai.schemas.diagnosis_views import ConversationView, PageView
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.services.task_access import TaskAccess


@overload
def instant(value: datetime) -> datetime: ...


@overload
def instant(value: None) -> None: ...


def instant(value: datetime | None) -> datetime | None:
    """MySQL UTC无时区列在公开边界恢复UTC，不按宿主本地时区解释。"""
    return value.replace(tzinfo=UTC) if value is not None else None


class ConversationService:
    """本人所有权和当前工单访问分别核验，咨询不继承排障工具或客户范围。"""

    def __init__(self, engine: AsyncEngine, access: TaskAccess) -> None:
        """资源归所属进程，不缓存用户权限。"""
        self.engine, self.access, self.rpc = engine, access, access.rpc

    async def authorized(self, actor: VerifiedIdentity, identifier: str) -> ConversationScope:
        """技术历史需当前工单访问，其他人有工单权限仍不能读私聊。"""
        value = await self.access.conversation(actor, identifier)
        if value.mode == "TECHNICIAN_ASSIST":
            await self.access.context(
                actor,
                value,
                service_item_id=None,
                device_id=None,
                reported=SupportEnvironment(),
                new_execution=False,
            )
        return value

    async def get(self, actor: VerifiedIdentity, identifier: str) -> ConversationView:
        """只返回会话管理字段，不把任务环境复制成另一份权威。"""
        await self.authorized(actor, identifier)
        async with self.engine.connect() as db:
            row = (
                (
                    await db.execute(
                        text("""
                SELECT id,title,mode,customer_id,ticket_id,row_version,created_at,updated_at
                FROM ai_conversation WHERE id=:id AND deleted_at IS NULL
            """),
                        {"id": identifier},
                    )
                )
                .mappings()
                .first()
            )
        if row is None:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        return ConversationView(
            **{
                **row,
                "row_version": str(row["row_version"]),
                "created_at": instant(row["created_at"]),
                "updated_at": instant(row["updated_at"]),
            }
        )

    async def create(self, actor: VerifiedIdentity, form: ConversationCreate) -> ConversationView:
        """拥有者范围原键重放先于新绑定检查；创建不隐式运行模型或创建任务。"""
        digest = canonical_hash(form.model_dump(mode="json"))
        async with self.engine.connect() as db:
            old = (
                await db.execute(
                    text("""
                SELECT id,request_hash FROM ai_conversation
                WHERE owner_id=:owner AND client_request_id=:key
            """),
                    {"owner": actor.actor.owner_id, "key": form.client_request_id},
                )
            ).first()
        if old:
            if old[1] != digest:
                raise BusinessError(CommonErrorCode.CONFLICT)
            return await self.get(actor, old[0])
        customer = actor.organization_id if "CUSTOMER_USER" in actor.roles else None
        if form.mode == "CUSTOMER_SELF_SERVICE" and not customer:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        if form.mode == "TECHNICIAN_ASSIST":
            if "TECHNICIAN" not in actor.roles:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
            try:
                context = await self.rpc.ticket.ReadDiagnosisContext(
                    TicketRequest(actor=actor.actor, ticket_id=form.ticket_id), **self.rpc.options()
                )
            except grpc.aio.AioRpcError as error:
                raise translate_failure(error) from None
            if context.ticket_id != form.ticket_id or not context.customer_id:
                raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
            customer = context.customer_id
        identifier = new_id()
        try:
            async with self.engine.begin() as db:
                await db.execute(
                    text("""
                    INSERT INTO ai_conversation(id,owner_id,customer_id,mode,ticket_id,
                                                client_request_id,request_hash,title)
                    VALUES(:id,:owner,:customer,:mode,:ticket,:key,:hash,:title)
                """),
                    {
                        "id": identifier,
                        "owner": actor.actor.owner_id,
                        "customer": customer,
                        "mode": form.mode,
                        "ticket": form.ticket_id,
                        "key": form.client_request_id,
                        "hash": digest,
                        "title": form.title,
                    },
                )
        except IntegrityError:
            async with self.engine.connect() as db:
                old = (
                    await db.execute(
                        text("""
                    SELECT id,request_hash FROM ai_conversation
                    WHERE owner_id=:owner AND client_request_id=:key
                """),
                        {"owner": actor.actor.owner_id, "key": form.client_request_id},
                    )
                ).first()
            if old is None or old[1] != digest:
                raise BusinessError(CommonErrorCode.CONFLICT) from None
            identifier = old[0]
        return await self.get(actor, identifier)

    async def list(
        self, actor: VerifiedIdentity, page: int, size: int, mode: str | None, ticket_id: str | None
    ) -> PageView[ConversationView]:
        """数据库有界分页；失去工单权的技术会话不返回标题。"""
        condition = "owner_id=:owner AND deleted_at IS NULL"
        if mode:
            condition += " AND mode=:mode"
        if ticket_id:
            condition += " AND ticket_id=:ticket"
        parameters = {
            "owner": actor.actor.owner_id,
            "mode": mode,
            "ticket": ticket_id,
            "limit": size,
            "offset": (page - 1) * size,
        }
        async with self.engine.connect() as db:
            count = (
                await db.execute(
                    text(f"SELECT COUNT(*) FROM ai_conversation WHERE {condition}"), parameters
                )
            ).scalar_one()
            identifiers = (
                (
                    await db.execute(
                        text(
                            f"SELECT id FROM ai_conversation WHERE {condition} "
                            "ORDER BY updated_at DESC,id LIMIT :limit OFFSET :offset"
                        ),
                        parameters,
                    )
                )
                .scalars()
                .all()
            )
        records = []
        for identifier in identifiers:
            try:
                records.append(await self.get(actor, identifier))
            except BusinessError as error:
                if error.error not in {CommonErrorCode.FORBIDDEN, CommonErrorCode.NOT_FOUND}:
                    raise
        return PageView(records=records, total=str(count), page_num=page, page_size=size)

    async def change(
        self,
        actor: VerifiedIdentity,
        identifier: str,
        form: ConversationChange,
        *,
        delete: bool = False,
    ) -> ConversationView | None:
        """归档先取消活动Run，任务须已结束，避免隐藏尚需反馈的工作。"""
        await self.authorized(actor, identifier)
        if not delete and form.title is None:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        async with self.engine.begin() as db:
            value = await scope(db, identifier, lock=True)
            self.access.owner(actor, value)
            current = (
                await db.execute(
                    text("SELECT row_version FROM ai_conversation WHERE id=:id"), {"id": identifier}
                )
            ).scalar_one()
            if current != int(form.row_version):
                raise BusinessError(CommonErrorCode.CONFLICT)
            if delete:
                active = (
                    await db.execute(
                        text("SELECT id FROM ai_diagnosis_task WHERE active_conversation_id=:id"),
                        {"id": identifier},
                    )
                ).first()
                if active:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                active_runs = (
                    (
                        await db.execute(
                            text("SELECT id FROM ai_run WHERE active_conversation_id=:id"),
                            {"id": identifier},
                        )
                    )
                    .scalars()
                    .all()
                )
                for run_id in active_runs:
                    await runs.stop(db, run_id, "CANCELLED", "CONVERSATION_ARCHIVED")
                await db.execute(
                    text(
                        "UPDATE ai_conversation SET deleted_at=UTC_TIMESTAMP(6),"
                        "row_version=row_version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=:id"
                    ),
                    {"id": identifier},
                )
            else:
                await db.execute(
                    text(
                        "UPDATE ai_conversation SET title=:title,row_version=row_version+1,"
                        "updated_at=UTC_TIMESTAMP(6) WHERE id=:id"
                    ),
                    {"id": identifier, "title": form.title},
                )
        return None if delete else await self.get(actor, identifier)
