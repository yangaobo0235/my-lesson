"""任务当前身份与业务范围校验；外部调用在数据库事务之外执行。作者：yangaobo。"""

import grpc
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.ticket.v1.support_pb2 import (
    ContextRequest,
    Environment,
    SupportContext,
    TicketRequest,
)
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.identity import translate_failure
from servicedesk_ai.repositories.conversation_scope import ConversationScope, scope
from servicedesk_ai.repositories.tasks import DiagnosisTask
from servicedesk_ai.schemas.diagnosis import SupportEnvironment


class TaskAccess:
    """会话私有范围和ticket当前资格分别核验，旧快照不授予新执行权限。"""

    def __init__(self, engine: AsyncEngine, rpc: DomainRpc) -> None:
        """复用所属进程资源，不缓存用户范围。"""
        self.engine, self.rpc = engine, rpc

    def owner(self, actor: VerifiedIdentity, value: ConversationScope) -> None:
        """角色、组织和固定拥有者都必须成立，咨询不会继承业务工具权限。"""
        if actor.actor.owner_id != value.owner_id:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        if value.mode == "CUSTOMER_SELF_SERVICE":
            if "CUSTOMER_USER" not in actor.roles or actor.organization_id != value.customer_id:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
        elif value.mode == "TECHNICIAN_ASSIST":
            if "TECHNICIAN" not in actor.roles:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
        elif value.customer_id is not None and actor.organization_id != value.customer_id:
            raise BusinessError(CommonErrorCode.FORBIDDEN)

    async def conversation(self, actor: VerifiedIdentity, identifier: str) -> ConversationScope:
        """先查所属关系，再进行外部范围检查，禁止以任务ID遍历他人目标。"""
        async with self.engine.connect() as db:
            value = await scope(db, identifier)
        self.owner(actor, value)
        return value

    async def context(
        self,
        actor: VerifiedIdentity,
        conversation: ConversationScope,
        *,
        service_item_id: str | None,
        device_id: str | None,
        reported: SupportEnvironment,
        new_execution: bool,
        history_ticket_id: str | None = None,
    ) -> SupportContext:
        """技术环境只能来自ticket，自助新执行还须当前开通资格。"""
        self.owner(actor, conversation)
        if conversation.mode == "CONSULT":
            raise BusinessError(CommonErrorCode.CONFLICT)
        try:
            if conversation.mode == "TECHNICIAN_ASSIST":
                value = await self.rpc.ticket.ReadDiagnosisContext(
                    TicketRequest(actor=actor.actor, ticket_id=conversation.ticket_id),
                    **self.rpc.options(),
                )
            else:
                value = await self.rpc.ticket.CheckContext(
                    ContextRequest(
                        actor=actor.actor,
                        service_item_id=service_item_id,
                        device_id=device_id,
                        purpose="NEW_SELF_SERVICE" if new_execution else "HISTORY",
                        ticket_id=history_ticket_id if not new_execution else None,
                        environment=Environment(**reported.model_dump(exclude_none=True)),
                    ),
                    **self.rpc.options(),
                )
        except grpc.aio.AioRpcError as error:
            raise translate_failure(error) from None
        if value.customer_id != conversation.customer_id:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        if history_ticket_id is not None and not new_execution:
            if value.ticket_id != history_ticket_id:
                raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
            return value
        if conversation.mode == "TECHNICIAN_ASSIST":
            if (
                value.ticket_id != conversation.ticket_id
                or not value.HasField("diagnosis_context_version")
                or not value.HasField("ticket_row_version")
                or not value.HasField("ticket_event_sequence")
            ):
                raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
        elif (value.service_item_id or None) != service_item_id or (
            value.device_id or None
        ) != device_id:
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
        return value

    async def authorize(
        self, actor: VerifiedIdentity, value: DiagnosisTask, *, new_execution: bool = False
    ) -> tuple[ConversationScope, SupportContext]:
        """任务环境来源元信息留在AI，跨服务只发送具名环境值。"""
        conversation = await self.conversation(actor, value.conversation_id)
        environment = SupportEnvironment.model_validate(
            value.support_context.model_dump(exclude={"schema_version", "field_sources"})
        )
        context = await self.context(
            actor,
            conversation,
            service_item_id=value.service_item_id,
            device_id=value.device_id,
            reported=environment,
            new_execution=new_execution,
            history_ticket_id=value.handover_ticket_id,
        )
        return conversation, context
