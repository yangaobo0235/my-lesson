"""共享反馈、处理与撤销竞争的具名行锁及投影；事务与授权由调用用例持有。作者：yangaobo。"""

from collections.abc import Mapping, Sequence

from sqlalchemy import text
from sqlalchemy.engine import Row, RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection


async def count_shared(db: AsyncConnection, parameters: Mapping[str, object]) -> int:
    """统计共享，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT COUNT(*) FROM ai_feedback f JOIN ai_generation g ON g.id=f.generation_id
                JOIN ai_conversation c ON c.id=g.conversation_id WHERE
                f.share_with_knowledge_admin=1 AND f.rating='UNHELPFUL' AND f.status IN
                ('PENDING','RESOLVED','DISMISSED') AND c.mode='CUSTOMER_SELF_SERVICE' AND
                (:status IS NULL OR f.status=:status) AND c.deleted_at IS NULL
            """
            ),
            parameters,
        )
    ).scalar_one()


async def page_shared(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[RowMapping]:
    """分页读取共享，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT
                f.id,f.generation_id,f.rating,f.issue_category,f.status,f.row_version,f.created_at
                FROM ai_feedback f JOIN ai_generation g ON g.id=f.generation_id JOIN
                ai_conversation c ON c.id=g.conversation_id WHERE f.share_with_knowledge_admin=1
                AND f.rating='UNHELPFUL' AND f.status IN ('PENDING','RESOLVED','DISMISSED') AND
                c.mode='CUSTOMER_SELF_SERVICE' AND (:status IS NULL OR f.status=:status) AND
                c.deleted_at IS NULL ORDER BY f.created_at DESC,f.id LIMIT :limit OFFSET :offset
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def shared_case(db: AsyncConnection, parameters: Mapping[str, object]) -> RowMapping | None:
    """读取共享案例，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT f.id,f.generation_id,f.rating,f.issue_category,f.comment,f.status,
                f.row_version,f.created_at,g.conversation_id,g.owner_id,g.session_ref,
                g.auth_version,g.status AS generation_status,g.question_text,g.answer_text,
                c.mode,c.deleted_at FROM ai_feedback f JOIN ai_generation g ON
                g.id=f.generation_id JOIN ai_conversation c ON c.id=g.conversation_id WHERE
                f.id=:id AND f.share_with_knowledge_admin=1 AND f.rating='UNHELPFUL' AND
                f.status IN ('PENDING','RESOLVED','DISMISSED') AND
                c.mode='CUSTOMER_SELF_SERVICE' AND c.deleted_at IS NULL
            """
                ),
                parameters,
            )
        )
        .mappings()
        .first()
    )


async def lock_feedback_case(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> RowMapping | None:
    """加锁读取反馈案例，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT f.id,f.generation_id,f.rating,f.issue_category,f.status,
                f.share_with_knowledge_admin,f.row_version,f.created_at,c.mode,c.deleted_at FROM
                ai_feedback f JOIN ai_generation g ON g.id=f.generation_id JOIN ai_conversation
                c ON c.id=g.conversation_id WHERE f.id=:id FOR UPDATE
            """
                ),
                parameters,
            )
        )
        .mappings()
        .first()
    )


async def handle_feedback(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """处理反馈，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_feedback SET status=:status,handled_by=:actor,handling_note=:note,
                handled_at=UTC_TIMESTAMP(6),row_version=row_version+1,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND row_version=:version AND status='PENDING'
            """
        ),
        parameters,
    )


async def lock_generation(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """加锁读取运行，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(text("SELECT id FROM ai_generation WHERE id=:id FOR UPDATE"), parameters)


async def lock_generation_feedback(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Row[tuple] | None:
    """加锁读取运行反馈，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text("SELECT id,status FROM ai_feedback WHERE generation_id=:id FOR UPDATE"), parameters
        )
    ).first()


async def update_feedback(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """更新反馈，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_feedback SET rating=:rating,issue_category=:category,
                comment=:comment,status=:status,row_version=row_version+1,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id
            """
        ),
        parameters,
    )


async def insert_feedback(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记反馈，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_feedback
                (id,generation_id,submitted_by,rating,issue_category,comment,share_with_knowledge_admin,status)
                VALUES (:id,:run,:actor,:rating,:category,:comment,false,:status)
            """
        ),
        parameters,
    )


async def lock_feedback_sharing(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Row[tuple] | None:
    """加锁读取反馈共享，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT id,row_version,status,rating FROM ai_feedback WHERE generation_id=:id FOR
                UPDATE
            """
            ),
            parameters,
        )
    ).first()


async def change_feedback_sharing(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """变更反馈共享，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_feedback SET share_with_knowledge_admin=:share,
                row_version=row_version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """
        ),
        parameters,
    )


async def feedback_view(db: AsyncConnection, parameters: Mapping[str, object]) -> RowMapping:
    """读取反馈投影，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id,rating,issue_category,comment,share_with_knowledge_admin,
                status,row_version FROM ai_feedback WHERE id=:id
            """
                ),
                parameters,
            )
        )
        .mappings()
        .one()
    )
