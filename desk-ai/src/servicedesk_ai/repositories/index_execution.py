"""解析身份对账和索引执行阶段的持久化；事务与授权由调用用例持有。作者：yangaobo。"""

from collections.abc import Mapping, Sequence

from sqlalchemy import text
from sqlalchemy.engine import RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection


async def chunk_count(db: AsyncConnection, parameters: Mapping[str, object]) -> int:
    """统计分块数量，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(text("SELECT COUNT(*) FROM ai_chunk WHERE index_job_id=:id"), parameters)
    ).scalar_one()


async def mark_indexing(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """标记索引阶段，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_index_job SET stage='INDEXING',updated_at=UTC_TIMESTAMP(6) WHERE
                id=:id
            """),
        parameters,
    )


async def mark_parser_unknown(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """标记解析结果未知，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("UPDATE ai_index_job SET parser_state='UNKNOWN' WHERE id=:id"), parameters
    )


async def mark_parser_submitting(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """标记解析提交中，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("UPDATE ai_index_job SET parser_state='SUBMITTING' WHERE id=:id"), parameters
    )


async def mark_unconfirmed_parser_unknown(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> None:
    """标记未确认解析结果未知，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_index_job SET parser_state='UNKNOWN' WHERE id=:id AND
                parser_state='SUBMITTING' AND parser_task_id IS NULL
            """
        ),
        parameters,
    )


async def save_parser_identity(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """保存解析身份，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_index_job SET parser_state='ACTIVE',parser_task_id=:task,
                parser_file_id=:file WHERE id=:id AND parser_state='SUBMITTING' AND
                parser_task_id IS NULL
            """
        ),
        parameters,
    )


async def settle_parser(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """收敛解析，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("UPDATE ai_index_job SET parser_state='SETTLED' WHERE id=:id"), parameters
    )


async def unsettled_parsers(db: AsyncConnection) -> Sequence[RowMapping]:
    """读取未收敛解析任务，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id,parser_task_id,parser_file_id FROM ai_index_job WHERE status IN
                ('FAILED','CANCELLED') AND parser_state='ACTIVE' ORDER BY updated_at,id LIMIT 20
            """
                )
            )
        )
        .mappings()
        .all()
    )


async def settle_original_parser(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """收敛原任务解析，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_index_job SET parser_state='SETTLED', updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND status IN ('FAILED','CANCELLED') AND parser_state='ACTIVE' AND
                parser_task_id=:task AND parser_file_id=:file
            """
        ),
        parameters,
    )
