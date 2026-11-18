"""人工动作草稿、幂等回执与当前事实的查询；事务与授权由调用用例持有。作者：yangaobo。"""

from collections.abc import Mapping
from datetime import datetime, timedelta

from sqlalchemy import text
from sqlalchemy.engine import Row, RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection

from servicedesk_ai.schemas.tasks import TaskEventDetails


async def read_draft(db: AsyncConnection, parameters: Mapping[str, object]) -> RowMapping | None:
    """读取草稿，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id,owner_id,conversation_id,task_id,diagnosis_context_version,
                source_refs,source_run_id,purpose,ticket_id,
                ticket_row_version,task_state_version,content,expires_at,draft_root_id,revision_number,
                previous_draft_id,content_sha256,status FROM ai_action_draft WHERE
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


async def synchronized_receipt(
    db: AsyncConnection, task_id: str, receipt_id: str
) -> TaskEventDetails | None:
    """同一正式回执跨请求键只推进一次任务，锁序由用例保证。"""
    raw = (
        await db.execute(
            text("""
        SELECT details FROM ai_task_event WHERE task_id=:task
        AND event_type='DRAFT_CONFIRMED'
        AND JSON_UNQUOTE(details->'$.action_receipt_id')=:receipt LIMIT 1
    """),
            {"task": task_id, "receipt": receipt_id},
        )
    ).scalar_one_or_none()
    return TaskEventDetails.model_validate_json(raw) if raw is not None else None


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
                task_id=:task AND reported_by=:owner AND
                source_type='USER_REPORT' AND operation<>'RETRACT' AND NOT EXISTS (SELECT 1 FROM
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
                (id,owner_id,conversation_id,task_id,diagnosis_context_version,source_refs,source_run_id,purpose,ticket_id,ticket_row_version,task_state_version,
                client_request_id,request_hash,content,expires_at,draft_root_id,revision_number,
                previous_draft_id,content_sha256,status) VALUES
                (:id,:owner,:conversation,:task,:context,:refs,:run,:purpose,:ticket,:ticket_row_version,:version,:key,:hash,:content,
                COALESCE(:expires_at,UTC_TIMESTAMP(6)+INTERVAL 24 HOUR),
                :root,:revision,:previous,:content_hash,'PENDING')
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


async def lock_draft(db: AsyncConnection, identifier: str) -> None:
    """由用例先锁会话再锁根，所有修订和撤回保持同一顺序。"""
    await db.execute(
        text("SELECT id FROM ai_action_draft WHERE id=:id FOR UPDATE"), {"id": identifier}
    )


async def set_status(db: AsyncConnection, identifier: str, status: str) -> None:
    """只推进当前待确认修订，调用方持有会话及根锁。"""
    await db.execute(
        text("UPDATE ai_action_draft SET status=:status WHERE id=:id AND status='PENDING'"),
        {"id": identifier, "status": status},
    )


async def waiting_roots(db: AsyncConnection, after_id: str) -> list[RowMapping]:
    """每批20个阻断根，使用游标公平扫描，持久状态不依赖维护器内存。"""
    return list(
        (
            await db.execute(
                text("""
        SELECT d.id,d.draft_root_id,d.owner_id,d.task_id,d.conversation_id
        FROM ai_action_draft d JOIN ai_diagnosis_task t ON t.id=d.task_id
        WHERE d.revision_number=1 AND d.id>:after AND (
            (t.status='WAITING_APPROVAL' AND t.blocking_draft_root_id=d.id)
            OR (EXISTS(SELECT 1 FROM ai_task_event e WHERE e.task_id=t.id
                AND e.event_type='DRAFT_RELEASED'
                AND JSON_UNQUOTE(e.details->'$.draft_root_id')=d.id)
                AND NOT EXISTS(SELECT 1 FROM ai_task_event e WHERE e.task_id=t.id
                AND e.event_type='DRAFT_CONFIRMED'
                AND JSON_UNQUOTE(e.details->'$.draft_root_id')=d.id))
        )
        ORDER BY d.id LIMIT 20
    """),
                {"after": after_id},
            )
        ).mappings()
    )


async def releasable_root(db: AsyncConnection, root: str) -> bool:
    """等待最后修订撤回或到期，额外留足已签发证明窗口；不证明Java未提交。"""
    row = (
        (
            await db.execute(
                text("""
        SELECT d.status,d.expires_at,UTC_TIMESTAMP(6) AS now,
            (SELECT MAX(e.created_at) FROM ai_task_event e WHERE e.task_id=d.task_id
             AND e.event_type='DRAFT_WITHDRAWN'
             AND JSON_UNQUOTE(e.details->'$.draft_root_id')=d.draft_root_id) AS withdrawn_at
        FROM ai_action_draft d WHERE d.draft_root_id=:root
        ORDER BY d.revision_number DESC LIMIT 1
    """),
                {"root": root},
            )
        )
        .mappings()
        .one()
    )
    return row["expires_at"] + timedelta(seconds=30) <= row["now"] or (
        row["status"] == "WITHDRAWN"
        and row["withdrawn_at"] is not None
        and row["withdrawn_at"] + timedelta(seconds=30) <= row["now"]
    )
