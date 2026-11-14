"""私有会话、生成执行权与事实的本地事务持久化，不调用外部服务。作者：yangaobo。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from uuid import uuid4

from sqlalchemy import text
from sqlalchemy.engine import RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode


def new_id() -> str:
    """所属后端生成32位业务标识，与执行令牌分开。"""
    return uuid4().hex


def json_value(value: object) -> str:
    """持久化版本化JSON，不保存凭据或连接对象。"""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


@dataclass(frozen=True)
class Conversation:
    """会话固定绑定和当前版本；快照不作为授权事实。"""

    id: str
    owner_id: str
    customer_id: str
    mode: str
    ticket_id: str | None
    service_item_id: str | None
    device_id: str | None
    product_model_id: str | None
    support_context: dict[str, object]
    state_version: int
    title: str
    deleted_at: datetime | None
    row_version: int
    created_at: datetime
    updated_at: datetime


@dataclass(frozen=True)
class Generation:
    """单轮完整数据库快照；终态写入另比较令牌和租约。"""

    id: str
    conversation_id: str
    owner_id: str
    round_number: int
    input_state_version: int
    output_state_version: int | None
    ticket_version: int | None
    question_text: str
    answer_text: str | None
    structured_result: dict[str, object] | None
    client_request_id: str
    request_hash: str
    session_ref: str
    auth_version: int
    status: str
    answer_type: str | None
    pipeline_config: dict[str, object]
    context_snapshot: dict[str, object]
    trace_context: dict[str, object] | None
    answer_model: str | None
    claim_token: str | None
    claim_until: datetime | None
    started_at: datetime | None
    finished_at: datetime | None
    latency_ms: int | None
    input_tokens: int | None
    output_tokens: int | None
    error_code: str | None
    created_at: datetime
    updated_at: datetime


def decode[T: (Conversation, Generation)](row: RowMapping, kind: type[T]) -> T:
    """只接受所属数据类声明列，数据库JSON在仓储边界解码。"""
    values = {name: row[name] for name in kind.__dataclass_fields__}
    for name in (
        "support_context",
        "structured_result",
        "pipeline_config",
        "context_snapshot",
        "trace_context",
    ):
        if name in values and isinstance(values[name], str):
            values[name] = json.loads(values[name])
    return kind(**values)


async def conversation(db: AsyncConnection, identifier: str, *, lock: bool = False) -> Conversation:
    """不存在和已删均不可作为新业务目标，删除对账另按SQL明确处理。"""
    row = (
        (
            await db.execute(
                text(
                    """
                SELECT id,owner_id,customer_id,mode,ticket_id,service_item_id,
                device_id,product_model_id,support_context,state_version,title,deleted_at,row_version,
                created_at,updated_at FROM ai_conversation WHERE id=:id
            """
                    + (" FOR UPDATE" if lock else "")
                ),
                {"id": identifier},
            )
        )
        .mappings()
        .first()
    )
    if row is None or row["deleted_at"] is not None:
        raise BusinessError(CommonErrorCode.NOT_FOUND)
    return decode(row, Conversation)


async def generation(db: AsyncConnection, identifier: str, *, lock: bool = False) -> Generation:
    """只取单轮明确列，生成列不进入用例快照。"""
    columns = ",".join(Generation.__dataclass_fields__)
    row = (
        (
            await db.execute(
                text(
                    f"SELECT {columns} FROM ai_generation WHERE id=:id"
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
    return decode(row, Generation)


async def generation_receipt(db: AsyncConnection, owner: str, key: str) -> Generation | None:
    """拥有者原键先于新版本及额度检查。"""
    row = (
        await db.execute(
            text("SELECT id FROM ai_generation WHERE owner_id=:owner AND client_request_id=:key"),
            {"owner": owner, "key": key},
        )
    ).first()
    return None if row is None else await generation(db, row[0])


async def require_idle(db: AsyncConnection, identifier: str) -> None:
    """与会话行锁配合，禁止活跃轮期间更正事实。"""
    active = (
        await db.execute(
            text(
                "SELECT id FROM ai_generation WHERE conversation_id=:id AND status IN "
                "('QUEUED','RUNNING') LIMIT 1"
            ),
            {"id": identifier},
        )
    ).first()
    if active:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def owned(db: AsyncConnection, run: Generation) -> None:
    """取消、过期执行和旧令牌一律不能写正文、工具结果或事实。"""
    row = (
        await db.execute(
            text("""
                SELECT id FROM ai_generation WHERE id=:id AND status='RUNNING'
                AND claim_token=:token AND claim_until>UTC_TIMESTAMP(6) FOR UPDATE
            """),
            {"id": run.id, "token": run.claim_token},
        )
    ).first()
    if row is None:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def claim(db: AsyncConnection) -> Generation | None:
    """超期RUNNING中断且不重新付费；只领取未过总预算的QUEUED。"""
    expired = (
        (
            await db.execute(
                text("""
                SELECT id FROM ai_generation WHERE status='RUNNING'
                AND claim_until<UTC_TIMESTAMP(6) ORDER BY claim_until,id LIMIT 20 FOR UPDATE
                SKIP
                LOCKED
            """)
            )
        )
        .scalars()
        .all()
    )
    for identifier in expired:
        await db.execute(
            text("""
                UPDATE ai_model_call SET status='UNKNOWN',error_code='WORKER_INTERRUPTED',
                finished_at=UTC_TIMESTAMP(6) WHERE generation_id=:id AND status='STARTED'
            """),
            {"id": identifier},
        )
        await db.execute(
            text("""
                UPDATE ai_tool_call SET status='UNKNOWN',error_code='WORKER_INTERRUPTED',
                finished_at=UTC_TIMESTAMP(6) WHERE generation_id=:id AND status='STARTED'
            """),
            {"id": identifier},
        )
        await db.execute(
            text("""
                UPDATE ai_generation SET status='INTERRUPTED',error_code='WORKER_INTERRUPTED',
                claim_token=NULL,claim_until=NULL,finished_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND status='RUNNING' AND claim_until<UTC_TIMESTAMP(6)
            """),
            {"id": identifier},
        )
    row = (
        await db.execute(
            text("""
                SELECT id,created_at FROM ai_generation WHERE status='QUEUED'
                ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
        )
    ).first()
    if row is None:
        return None
    now = (await db.execute(text("SELECT UTC_TIMESTAMP(6)"))).scalar_one()
    run = await generation(db, row[0])
    if (now - run.created_at).total_seconds() >= int(run.pipeline_config["total_seconds"]):
        await db.execute(
            text("""
                UPDATE ai_generation SET status='FAILED',error_code='QUEUE_BUDGET_EXCEEDED',
                finished_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
            {"id": run.id},
        )
        return None
    await db.execute(
        text("""
                UPDATE ai_generation SET status='RUNNING',started_at=UTC_TIMESTAMP(6),
                claim_token=:token,claim_until=UTC_TIMESTAMP(6)+INTERVAL :lease
                SECOND,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id
            """),
        {"id": run.id, "token": str(uuid4()), "lease": run.pipeline_config["lease_seconds"]},
    )
    return await generation(db, run.id)


async def renew(db: AsyncConnection, run: Generation) -> bool:
    """续约不能复活过期租约。"""
    result = await db.execute(
        text("""
                UPDATE ai_generation SET claim_until=UTC_TIMESTAMP(6)+INTERVAL :lease SECOND,
                updated_at=UTC_TIMESTAMP(6) WHERE id=:id AND status='RUNNING' AND
                claim_token=:token
                AND claim_until>UTC_TIMESTAMP(6)
            """),
        {"id": run.id, "token": run.claim_token, "lease": run.pipeline_config["lease_seconds"]},
    )
    return result.rowcount == 1


async def stop(db: AsyncConnection, run: Generation, status: str, error: str | None) -> None:
    """调用者已持有生成行锁；终态清空执行权，不提交任何迟到输出。"""
    if status not in {"CANCELLED", "FAILED", "INTERRUPTED"}:
        raise ValueError("非法停止终态")
    await db.execute(
        text("""
                UPDATE ai_model_call SET status='UNKNOWN',error_code='GENERATION_STOPPED',
                finished_at=UTC_TIMESTAMP(6) WHERE generation_id=:id AND status='STARTED'
            """),
        {"id": run.id},
    )
    await db.execute(
        text("""
                UPDATE ai_tool_call SET status='CANCELLED',error_code='GENERATION_STOPPED',
                finished_at=UTC_TIMESTAMP(6) WHERE generation_id=:id AND status='STARTED'
            """),
        {"id": run.id},
    )
    await db.execute(
        text("""
                UPDATE ai_generation SET status=:status,error_code=:error,
                finished_at=UTC_TIMESTAMP(6),claim_token=NULL,claim_until=NULL,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND status IN ('QUEUED','RUNNING')
            """),
        {"id": run.id, "status": status, "error": error},
    )
