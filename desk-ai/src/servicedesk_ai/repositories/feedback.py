"""本人质量反馈的有界投影和条件写入；事务由用例持有。作者：yangaobo。"""

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.schemas.diagnosis_views import FeedbackView


async def read_feedback(
    db: AsyncConnection, run_id: str, owner_id: str, *, lock: bool = False
) -> FeedbackView | None:
    """始终同时限定运行和拥有者，只有更新用例请求行锁。"""
    statement = """
        SELECT id,run_id,rating,adoption,issue_category,comment,row_version
        FROM ai_feedback WHERE run_id=:run AND submitted_by=:owner
    """
    row = (
        (
            await db.execute(
                text(statement + (" FOR UPDATE" if lock else "")),
                {"run": run_id, "owner": owner_id},
            )
        )
        .mappings()
        .first()
    )
    if row is None:
        return None
    return FeedbackView.model_validate({**row, "row_version": str(row["row_version"])})


async def update_feedback(
    db: AsyncConnection, identifier: str, version: int, content: dict[str, object]
) -> None:
    """版本冲突明确失败，更新时间和评价同事务提交。"""
    result = await db.execute(
        text("""
        UPDATE ai_feedback SET rating=:rating,adoption=:adoption,issue_category=:issue_category,
          comment=:comment,row_version=row_version+1,updated_at=UTC_TIMESTAMP(6)
        WHERE id=:id AND row_version=:version
    """),
        {"id": identifier, "version": version, **content},
    )
    if result.rowcount != 1:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def insert_feedback(
    db: AsyncConnection, identifier: str, run_id: str, owner_id: str, content: dict[str, object]
) -> None:
    """运行锁串行首次写入，数据库唯一约束提供最后防线。"""
    await db.execute(
        text("""
        INSERT INTO ai_feedback (id,run_id,submitted_by,rating,adoption,issue_category,comment)
        VALUES (:id,:run,:owner,:rating,:adoption,:issue_category,:comment)
    """),
        {"id": identifier, "run": run_id, "owner": owner_id, **content},
    )
