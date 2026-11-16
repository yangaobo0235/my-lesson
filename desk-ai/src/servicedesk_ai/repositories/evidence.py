"""实际来源清单、事实依赖与索引正文的持久化；事务与授权由调用用例持有。作者：yangaobo。"""

from collections.abc import Mapping, Sequence

from sqlalchemy import text
from sqlalchemy.engine import RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection


async def source_usages(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[RowMapping]:
    """读取来源使用清单，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id,source_number,source_type,chunk_id,ticket_event_id,
                fact_id,source_version,locator_snapshot,excerpt_snapshot FROM ai_source_usage
                WHERE generation_id=:id ORDER BY source_number
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def fact_dependency(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> RowMapping | None:
    """读取事实依赖，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT conversation_id,source_type,source_generation_id FROM ai_diagnostic_fact
                WHERE id=:id
            """
                ),
                parameters,
            )
        )
        .mappings()
        .first()
    )


async def generation_sources(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[RowMapping]:
    """读取运行来源，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id,source_number,chunk_id,ticket_event_id,fact_id,
                excerpt_snapshot,source_version FROM ai_source_usage WHERE generation_id=:id
                ORDER BY source_number
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def insert_source_usage(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记来源使用记录，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_source_usage
                (id,generation_id,conversation_id,source_number,source_type,chunk_id,ticket_event_id,fact_id,
                source_version,locator_snapshot,excerpt_snapshot) VALUES
                (:id,:run,:conversation,:number,:type,:chunk,:event,:fact,:version,:locator,:excerpt)
            """
        ),
        parameters,
    )


async def update_source_manifest(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """更新来源清单，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_generation SET context_snapshot=JSON_SET(context_snapshot,
                '$.source_usage_ids',CAST(:manifest AS JSON)) WHERE id=:id
            """
        ),
        parameters,
    )


async def readable_chunk(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> RowMapping | None:
    """读取可用分块，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT c.id,c.index_job_id,c.document_id,c.content,c.source_locations,
                j.status,j.last_command FROM ai_chunk c JOIN ai_index_job j ON
                j.id=c.index_job_id WHERE c.id=:id
            """
                ),
                parameters,
            )
        )
        .mappings()
        .first()
    )
