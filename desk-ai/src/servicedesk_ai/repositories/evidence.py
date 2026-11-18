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
                fact_id,message_id,attachment_id,procedure_document_id,procedure_check_key,title_snapshot,usage_kind,
                source_version,locator_snapshot,excerpt_snapshot FROM ai_source_usage
                WHERE run_id=:id ORDER BY source_number
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
                SELECT t.conversation_id,f.task_id,f.source_type,c.run_id AS source_run_id
                FROM ai_diagnostic_fact f JOIN ai_diagnosis_task t ON t.id=f.task_id
                LEFT JOIN ai_tool_call c ON c.id=f.source_tool_call_id WHERE f.id=:id
            """
                ),
                parameters,
            )
        )
        .mappings()
        .first()
    )


async def run_sources(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[RowMapping]:
    """读取运行来源，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id,source_number,chunk_id,ticket_event_id,fact_id,message_id,attachment_id,
                procedure_document_id,procedure_check_key,
                excerpt_snapshot,source_version,usage_kind FROM ai_source_usage WHERE run_id=:id
                ORDER BY source_number
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def message_dependency(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> RowMapping | None:
    """助手消息通过原运行核验来源；消息摘要保护已固定的历史内容。"""
    return (
        (
            await db.execute(
                text("""
            SELECT m.conversation_id,m.task_id,m.role,m.content_sha256,r.id AS source_run_id
            FROM ai_message m LEFT JOIN ai_run r ON r.output_message_id=m.id
            WHERE m.id=:id
        """),
                parameters,
            )
        )
        .mappings()
        .first()
    )


async def insert_source_usage(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记来源使用记录，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_source_usage
                (id,run_id,source_number,source_type,chunk_id,ticket_event_id,fact_id,message_id,
                 attachment_id,procedure_document_id,procedure_check_key,
                 title_snapshot,usage_kind,source_version,locator_snapshot,excerpt_snapshot)
                VALUES (:id,:run,:number,:type,:chunk,:event,:fact,:message,:attachment,
                        :procedure,:check_key,:title,:usage,:version,:locator,:excerpt)
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
                c.parent_chunk_id,c.node_kind,c.content_sha256,c.chunk_number,
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


async def adjacent_sections(db: AsyncConnection, identifier: str) -> Sequence[RowMapping]:
    """只沿同任务和相同真实章节读取连续分段；分段定位不能成为跨文档入口。"""
    return (
        (
            await db.execute(
                text("""
        SELECT c.id,c.chunk_number FROM ai_chunk c JOIN ai_chunk origin
            ON origin.index_job_id=c.index_job_id AND (origin.section_path<=>c.section_path)
            AND (origin.source_locations->'$.segments[0].anchor'
                 <=>c.source_locations->'$.segments[0].anchor')
        WHERE origin.id=:id AND c.node_kind='SECTION' ORDER BY c.chunk_number
    """),
                {"id": identifier},
            )
        )
        .mappings()
        .all()
    )
