"""私有会话固定授权范围，不包含或维护诊断环境投影。作者：yangaobo。"""

from dataclasses import dataclass
from typing import Literal

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode


@dataclass(frozen=True)
class ConversationScope:
    """固定所属人与业务目标，任何工单权限都不能扩大为他人会话权限。"""

    id: str
    owner_id: str
    customer_id: str | None
    mode: Literal["CONSULT", "CUSTOMER_SELF_SERVICE", "TECHNICIAN_ASSIST"]
    ticket_id: str | None


async def scope(db: AsyncConnection, identifier: str, *, lock: bool = False) -> ConversationScope:
    """所有任务修改先取得本行锁，逻辑归档立即禁止新操作。"""
    row = (
        (
            await db.execute(
                text(
                    """
        SELECT id,owner_id,customer_id,mode,ticket_id FROM ai_conversation
        WHERE id=:id AND deleted_at IS NULL
    """
                    + (" FOR UPDATE" if lock else "")
                ),
                {"id": identifier},
            )
        )
        .mappings()
        .first()
    )
    if row is None:
        raise BusinessError(CommonErrorCode.NOT_FOUND)
    return ConversationScope(**row)
