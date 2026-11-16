"""人工动作草稿、幂等回执与当前事实的查询；事务与授权由调用用例持有。作者：yangaobo。"""

from collections.abc import Mapping
from datetime import datetime

from sqlalchemy import text
from sqlalchemy.engine import Row, RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection


async def read_draft(db: AsyncConnection, parameters: Mapping[str, object]) -> RowMapping | None:
    """读取草稿，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id,owner_id,conversation_id,source_generation_id,purpose,ticket_id,
                ticket_version,state_version,content,expires_at FROM ai_action_draft WHERE
                id=:id
            """
                ),
                parameters,
            )
        )
        .mappings()
        .first()
    )


async def database_now(db: AsyncConnection) -> datetime:
    """读取数据库当前时间，沿调用方连接执行，不开启或提交事务。"""
    return (await db.execute(text("SELECT UTC_TIMESTAMP(6)"))).scalar_one()


async def find_draft_request(db: AsyncConnection, parameters: Mapping[str, object]) -> str | None:
    """查询草稿请求回执，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text("""
                SELECT id FROM ai_action_draft WHERE owner_id=:owner AND client_request_id=:key
            """),
            parameters,
        )
    ).scalar_one_or_none()


async def draft_request_receipt(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Row[tuple] | None:
    """读取草稿请求回执回执，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT id,request_hash FROM ai_action_draft WHERE owner_id=:owner AND
                client_request_id=:key
            """
            ),
            parameters,
        )
    ).first()


async def confirmed_user_fact(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Row[tuple] | None:
    """读取已确认用户事实，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT fact_kind,statement FROM ai_diagnostic_fact f WHERE id=:fact AND
                conversation_id=:conversation AND reported_by=:owner AND
                source_type='USER_REPORT' AND operation='ASSERT' AND NOT EXISTS (SELECT 1 FROM
                ai_diagnostic_fact s WHERE s.supersedes_fact_id=f.id)
            """
            ),
            parameters,
        )
    ).first()


async def insert_draft(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记草稿，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_action_draft
                (id,owner_id,conversation_id,source_generation_id,purpose,ticket_id,ticket_version,state_version,
                client_request_id,request_hash,content,expires_at) VALUES
                (:id,:owner,:conversation,:run,:purpose,:ticket,:ticket_version,:version,:key,:hash,:content,
                UTC_TIMESTAMP(6)+INTERVAL 24 HOUR)
            """
        ),
        parameters,
    )


async def draft_conversation(db: AsyncConnection, parameters: Mapping[str, object]) -> str:
    """读取草稿会话，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text("SELECT conversation_id FROM ai_action_draft WHERE id=:id"), parameters
        )
    ).scalar_one()
