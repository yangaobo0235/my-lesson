"""持久Run执行权、全局受理闸门及失联收敛；不自动重放付费调用。作者：yangaobo。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from typing import Literal
from uuid import uuid4

from sqlalchemy import text
from sqlalchemy.engine import RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories.call_usage import interrupt_calls
from servicedesk_ai.schemas.runs import RunContext, RunPipeline


@dataclass(frozen=True)
class RunLease:
    """执行器所需的输入与执行权投影，结果正文不在领取查询中加载。"""

    id: str
    conversation_id: str
    task_id: str | None
    owner_id: str
    input_message_id: str
    resumes_run_id: str | None
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "INTERRUPTED"]
    input_state_version: int | None
    diagnosis_context_version: int | None
    ticket_row_version: int | None
    ticket_event_sequence: int | None
    session_ref: str
    auth_version: int
    pipeline_config: RunPipeline
    context_manifest: RunContext
    execution_phase: Literal["CONTEXT", "MODEL", "TOOL", "COMMIT"]
    iteration_number: int
    deadline_at: datetime
    claim_token: str | None
    claim_until: datetime | None
    started_at: datetime | None
    finished_at: datetime | None
    created_at: datetime
    updated_at: datetime
    trace_context: dict[str, object] | None = None


@dataclass(frozen=True)
class RunReceipt:
    """拥有者范围原键回执，只用于重放前核对语义。"""

    id: str
    conversation_id: str
    request_hash: str


async def receipt(db: AsyncConnection, owner: str, key: str) -> RunReceipt | None:
    """查回执不依赖Redis、当前预算或可执行状态。"""
    row = (
        (
            await db.execute(
                text("""
        SELECT id,conversation_id,request_hash FROM ai_run
        WHERE owner_id=:owner AND client_request_id=:key
    """),
                {"owner": owner, "key": key},
            )
        )
        .mappings()
        .first()
    )
    return None if row is None else RunReceipt(**row)


async def context_rows(
    db: AsyncConnection, run: RunLease
) -> tuple[list[RowMapping], list[RowMapping], list[RowMapping]]:
    """按受理时的消息摘要、事实版本和检查版本加载模型候选输入。"""
    history: list[RowMapping] = []
    facts: list[RowMapping] = []
    checks: list[RowMapping] = []
    for reference in run.context_manifest.messages:
        row = (
            (
                await db.execute(
                    text("""
            SELECT m.id,m.role,m.content,m.content_sha256,m.task_id,
                   r.id AS source_run_id
            FROM ai_message m LEFT JOIN ai_run r ON r.output_message_id=m.id
            WHERE m.id=:id AND m.conversation_id=:conversation
        """),
                    {"id": reference.id, "conversation": run.conversation_id},
                )
            )
            .mappings()
            .one()
        )
        if row["content_sha256"] != reference.content_sha256 or row["task_id"] != run.task_id:
            raise BusinessError(CommonErrorCode.CONFLICT)
        history.append(row)
    for fact_reference in run.context_manifest.facts:
        row = (
            (
                await db.execute(
                    text("""
            SELECT f.id,f.statement,f.source_type,f.environment_snapshot,f.state_version,
                   f.fact_kind,f.created_at,
                   c.run_id AS source_run_id
            FROM ai_diagnostic_fact f LEFT JOIN ai_tool_call c ON c.id=f.source_tool_call_id
            WHERE f.id=:id AND f.task_id=:task AND f.state_version=:version
        """),
                    {
                        "id": fact_reference.id,
                        "task": run.task_id,
                        "version": fact_reference.state_version,
                    },
                )
            )
            .mappings()
            .one()
        )
        facts.append(row)
    for check_reference in run.context_manifest.checks:
        check_row = (
            (
                await db.execute(
                    text("""
            SELECT c.id,c.check_key,c.check_kind,c.instruction,c.status,c.normalized_answer,
                   c.context_version,c.proposed_run_id,c.procedure_document_id,
                   c.procedure_check_key,c.answer_message_id,c.answered_at,c.row_version,
                   c.result_fact_id,
                   m.content_sha256 AS answer_sha256
            FROM ai_diagnostic_check c LEFT JOIN ai_message m
              ON m.id=c.answer_message_id AND m.task_id=c.task_id AND m.role='USER'
            WHERE c.id=:id AND c.task_id=:task
              AND c.row_version=:version AND c.context_version=:context
        """),
                    {
                        "id": check_reference.id,
                        "task": run.task_id,
                        "version": check_reference.row_version,
                        "context": check_reference.context_version,
                    },
                )
            )
            .mappings()
            .one_or_none()
        )
        if check_row is None:
            raise BusinessError(CommonErrorCode.CONFLICT)
        checks.append(check_row)
    return history, facts, checks


async def save_context(db: AsyncConnection, value: RunLease) -> None:
    """首次模型调用前固定实际选中清单及来源化摘要，失去租约不能写回。"""
    await owned(db, value)
    changed = await db.execute(
        text(
            "UPDATE ai_run SET context_manifest=:manifest WHERE id=:id "
            "AND execution_phase='CONTEXT' AND iteration_number=0"
        ),
        {"manifest": value.context_manifest.model_dump_json(), "id": value.id},
    )
    if changed.rowcount != 1:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def run(db: AsyncConnection, identifier: str, *, lock: bool = False) -> RunLease:
    """JSON在领取边界校验；生成列和隐私正文不混入执行权快照。"""
    columns = ",".join(RunLease.__dataclass_fields__)
    row = (
        (
            await db.execute(
                text(
                    f"SELECT {columns} FROM ai_run WHERE id=:id" + (" FOR UPDATE" if lock else "")
                ),
                {"id": identifier},
            )
        )
        .mappings()
        .first()
    )
    if row is None:
        raise BusinessError(CommonErrorCode.NOT_FOUND)
    values = dict(row)
    values["pipeline_config"] = RunPipeline.model_validate_json(values["pipeline_config"])
    values["context_manifest"] = RunContext.model_validate_json(values["context_manifest"])
    if isinstance(values["trace_context"], str):
        values["trace_context"] = json.loads(values["trace_context"])
    return RunLease(**values)


async def lock_gate(db: AsyncConnection) -> None:
    """新受理固定锁gate→conversation→task，数据库串行化全局容量判断。"""
    identifier = (
        await db.execute(
            text("""
        SELECT id FROM ai_execution_gate WHERE gate_key='INTERACTIVE' FOR UPDATE
    """)
        )
    ).scalar_one_or_none()
    if identifier is None:
        raise RuntimeError("交互执行闸门缺失，拒绝受理")


async def queued_count(db: AsyncConnection) -> int:
    """调用者持gate行锁；不用进程内信号量声称实现全局排队上限。"""
    return (
        await db.execute(text("SELECT COUNT(*) FROM ai_run WHERE status='QUEUED'"))
    ).scalar_one()


async def owned(db: AsyncConnection, value: RunLease) -> None:
    """所有外调意图和结果必须仍持原令牌，租约与总Deadline分别校验。"""
    row = (
        await db.execute(
            text("""
        SELECT id FROM ai_run WHERE id=:id AND status='RUNNING' AND claim_token=:token
        AND claim_until>UTC_TIMESTAMP(6) AND deadline_at>UTC_TIMESTAMP(6) FOR UPDATE
    """),
            {"id": value.id, "token": value.claim_token},
        )
    ).first()
    if row is None:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def stop(
    db: AsyncConnection,
    identifier: str,
    status: Literal["FAILED", "CANCELLED", "INTERRUPTED"],
    error: str | None,
    *,
    token: str | None = None,
    expired_only: bool = False,
) -> bool:
    """终态与调用收敛原子提交；未发出是CANCELLED，已发出但未确认是UNKNOWN。"""
    condition = ""
    if token is not None:
        condition += " AND claim_token=:token"
    if expired_only:
        condition += (
            " AND (deadline_at<=UTC_TIMESTAMP(6) OR "
            "(status='RUNNING' AND claim_until<=UTC_TIMESTAMP(6)))"
        )
    result = await db.execute(
        text(
            """
        UPDATE ai_run SET status=:status,error_code=:error,finished_at=UTC_TIMESTAMP(6),
            claim_token=NULL,claim_until=NULL,updated_at=UTC_TIMESTAMP(6)
        WHERE id=:id AND status IN ('QUEUED','RUNNING')
    """
            + condition
        ),
        {"id": identifier, "status": status, "error": error, "token": token},
    )
    if result.rowcount != 1:
        return False
    await interrupt_calls(db, run_id=identifier)
    await db.execute(
        text("""
        UPDATE ai_tool_call SET status=CASE WHEN status='PREPARED'
            THEN 'CANCELLED' ELSE 'UNKNOWN' END,
            finished_at=UTC_TIMESTAMP(6) WHERE run_id=:id AND status IN ('PREPARED','RUNNING')
    """),
        {"id": identifier},
    )
    return True


async def expire(db: AsyncConnection) -> int:
    """有界收敛过期运行，保留原身份及未知账目，不重置为QUEUED。"""
    candidates = (
        await db.execute(
            text("""
        SELECT id,status FROM ai_run WHERE status IN ('QUEUED','RUNNING')
        AND (deadline_at<=UTC_TIMESTAMP(6) OR (status='RUNNING' AND claim_until<=UTC_TIMESTAMP(6)))
        ORDER BY deadline_at,id LIMIT 20 FOR UPDATE SKIP LOCKED
    """)
        )
    ).all()
    changed = 0
    for identifier, state in candidates:
        changed += await stop(
            db,
            identifier,
            "FAILED" if state == "QUEUED" else "INTERRUPTED",
            "QUEUE_DEADLINE_EXCEEDED" if state == "QUEUED" else "EXECUTION_RIGHT_EXPIRED",
            expired_only=True,
        )
    return changed


async def claim(db: AsyncConnection) -> RunLease | None:
    """只领取未过排队Deadline的QUEUED；历史中断不得自动再发付费请求。"""
    await expire(db)
    identifier = (
        await db.execute(
            text("""
        SELECT id FROM ai_run WHERE status='QUEUED' AND deadline_at>UTC_TIMESTAMP(6)
        ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
    """)
        )
    ).scalar_one_or_none()
    if identifier is None:
        return None
    value = await run(db, identifier)
    await db.execute(
        text("""
        UPDATE ai_run SET status='RUNNING',started_at=UTC_TIMESTAMP(6),claim_token=:token,
            claim_until=LEAST(deadline_at,UTC_TIMESTAMP(6)+INTERVAL :lease SECOND),
            updated_at=UTC_TIMESTAMP(6) WHERE id=:id AND status='QUEUED'
    """),
        {"id": identifier, "token": str(uuid4()), "lease": value.pipeline_config.lease_seconds},
    )
    return await run(db, identifier)


async def renew(db: AsyncConnection, value: RunLease) -> bool:
    """续约不能超出原总Deadline，也不能让过期令牌复活。"""
    result = await db.execute(
        text("""
        UPDATE ai_run SET claim_until=LEAST(deadline_at,UTC_TIMESTAMP(6)+INTERVAL :lease SECOND),
            updated_at=UTC_TIMESTAMP(6) WHERE id=:id AND status='RUNNING' AND claim_token=:token
            AND claim_until>UTC_TIMESTAMP(6) AND deadline_at>UTC_TIMESTAMP(6)
    """),
        {"id": value.id, "token": value.claim_token, "lease": value.pipeline_config.lease_seconds},
    )
    return result.rowcount == 1
