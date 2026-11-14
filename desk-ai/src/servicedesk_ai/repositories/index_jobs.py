"""索引任务、取消墓碑与租约的MySQL原子操作，调用者决定事务提交。作者：yangaobo。"""

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
from servicedesk_ai.schemas.indexing import IndexCommand, ParsedChunk, ProcessingConfig


@dataclass(frozen=True)
class IndexJob:
    """数据库领取快照；后续写入仍须比较令牌和有效租约。"""

    id: str
    document_id: str
    request_hash: str
    content_format: Literal["PDF", "MARKDOWN"]
    file_sha256: str
    last_command: str
    status: str
    stage: str | None
    parser_state: str
    parser_task_id: str | None
    parser_file_id: str | None
    page_count: int | None
    claim_token: str | None
    claim_until: datetime | None
    processing_config: ProcessingConfig
    trace_context: dict[str, object] | None


def decode_job(row: RowMapping) -> IndexJob:
    """仅由数据库明确列映射得到任务，不将供应商字典直接交给用例。"""
    values = dict(row)
    config = values.pop("processing_config")
    if isinstance(values.get("trace_context"), str):
        values["trace_context"] = json.loads(values["trace_context"])
    values["processing_config"] = ProcessingConfig.model_validate(
        json.loads(config) if isinstance(config, str) else config
    )
    return IndexJob(**values)


JOB_COLUMNS = (
    "id,document_id,request_hash,content_format,file_sha256,last_command,status,stage,"
    "parser_state,parser_task_id,parser_file_id,page_count,claim_token,claim_until,processing_config,trace_context"
)


async def get_job(db: AsyncConnection, job_id: str, *, lock: bool = False) -> IndexJob | None:
    """按唯一身份读取；锁仅在调用者短事务内持有。"""
    row = (
        (
            await db.execute(
                text(
                    f"SELECT {JOB_COLUMNS} FROM ai_index_job WHERE id=:id"
                    + (" FOR UPDATE" if lock else "")
                ),
                {"id": job_id},
            )
        )
        .mappings()
        .first()
    )
    return None if row is None else decode_job(row)


async def accept_command(
    db: AsyncConnection, command: IndexCommand, trace_context: dict[str, object] | None = None
) -> None:
    """落库成功后才允许ACK；取消先到保存完整墓碑，晚到提交不可复活。"""
    old = await get_job(db, command.job_id, lock=True)
    if old is not None:
        if old.request_hash != command.request_hash:
            raise BusinessError(CommonErrorCode.CONFLICT)
        if command.command == "CANCEL" and old.last_command != "CANCEL":
            await db.execute(
                text("""
                UPDATE ai_index_job SET last_command='CANCEL',
                finished_at=IF(status IN ('QUEUED','RUNNING'),UTC_TIMESTAMP(6),finished_at),
                status=IF(status IN ('QUEUED','RUNNING'),'CANCELLED',status),
                claim_token=NULL,claim_until=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
                {"id": command.job_id},
            )
        return
    cancelled = command.command == "CANCEL"
    await db.execute(
        text("""
                INSERT INTO ai_index_job
                (id,document_id,attempt_number,content_format,file_sha256,request_hash,requested_by,
                last_command,status,parser_state,processing_config,finished_at,trace_context)
                VALUES
                (:id,:document,:attempt,:format,:sha,:hash,:actor,:command,:status,:parser,
                :config,IF(:cancelled,UTC_TIMESTAMP(6),NULL),:trace)
            """),
        {
            "id": command.job_id,
            "document": command.document_id,
            "attempt": command.attempt_number,
            "format": command.content_format,
            "sha": command.file_sha256,
            "hash": command.request_hash,
            "actor": command.requested_by,
            "command": command.command,
            "status": "CANCELLED" if cancelled else "QUEUED",
            "parser": "NOT_REQUIRED" if command.content_format == "MARKDOWN" else "NOT_SUBMITTED",
            "config": command.processing_config.model_dump_json(),
            "cancelled": cancelled,
            "trace": None if trace_context is None else json.dumps(trace_context),
        },
    )


async def claim_job(db: AsyncConnection) -> IndexJob | None:
    """短事务SKIP LOCKED领取；已过期执行恢复原阶段及原解析身份。"""
    row = (
        await db.execute(
            text("""
                SELECT id FROM ai_index_job
                WHERE last_command='SUBMIT' AND (status='QUEUED' OR
                (status='RUNNING' AND claim_until<UTC_TIMESTAMP(6)))
                ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
        )
    ).first()
    if row is None:
        return None
    token = str(uuid4())
    await db.execute(
        text("""
                UPDATE ai_index_job SET status='RUNNING',stage=COALESCE(stage,'PARSING'),
                started_at=COALESCE(started_at,UTC_TIMESTAMP(6)),claim_token=:token,
                claim_until=UTC_TIMESTAMP(6)+INTERVAL 30 SECOND,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id
            """),
        {"id": row[0], "token": token},
    )
    return await get_job(db, row[0])


async def renew_job(db: AsyncConnection, job: IndexJob) -> bool:
    """只允许当前未过期执行者续约，旧进程暂停后不可追认。"""
    result = await db.execute(
        text("""
                UPDATE ai_index_job SET
                claim_until=UTC_TIMESTAMP(6)+INTERVAL 30 SECOND,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND status='RUNNING' AND claim_token=:token AND
                claim_until>UTC_TIMESTAMP(6)
            """),
        {"id": job.id, "token": job.claim_token},
    )
    return result.rowcount == 1


async def owned(db: AsyncConnection, job: IndexJob) -> None:
    """阶段与结果写入前锁定并核对当前有效令牌。"""
    row = (
        await db.execute(
            text("""
                SELECT id FROM ai_index_job WHERE id=:id
                AND status='RUNNING' AND claim_token=:token AND claim_until>UTC_TIMESTAMP(6) FOR
                UPDATE
            """),
            {"id": job.id, "token": job.claim_token},
        )
    ).first()
    if row is None:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def save_manifest(
    db: AsyncConnection, job: IndexJob, chunks: list[ParsedChunk], pages: int | None
) -> None:
    """一次固定完整清单及下一阶段；不存在部分清单成功。"""
    await owned(db, job)
    existing = await read_chunks(db, job.id)
    if existing:
        raise BusinessError(CommonErrorCode.CONFLICT)
    for number, chunk in enumerate(chunks, 1):
        await db.execute(
            text("""
                INSERT INTO ai_chunk
                (id,index_job_id,document_id,chunk_number,section_path,content,source_locations,error_codes)
                VALUES (:id,:job,:document,:number,:section,:content,:locations,:codes)
            """),
            {
                "id": chunk.id,
                "job": job.id,
                "document": job.document_id,
                "number": number,
                "section": chunk.section_path,
                "content": chunk.content,
                "locations": chunk.source_locations.model_dump_json(),
                "codes": json.dumps(chunk.error_codes),
            },
        )
    await db.execute(
        text("""
                UPDATE ai_index_job SET stage='EMBEDDING',page_count=:pages,
                updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
        {"id": job.id, "pages": pages},
    )


async def read_chunks(db: AsyncConnection, job_id: str) -> list[ParsedChunk]:
    """只读取指定任务固定清单，不把其他任务片段混入恢复。"""
    rows = (
        await db.execute(
            text("""
                SELECT id,section_path,content,source_locations,error_codes
                FROM ai_chunk WHERE index_job_id=:job ORDER BY chunk_number
            """),
            {"job": job_id},
        )
    ).mappings()
    return [
        ParsedChunk.model_validate(
            {
                **row,
                "source_locations": json.loads(row["source_locations"]),
                "error_codes": json.loads(row["error_codes"]),
            }
        )
        for row in rows
    ]


async def finish_job(db: AsyncConnection, job: IndexJob, error: str | None = None) -> None:
    """取消与终态争同一行；失去执行权的迟到结果不落库。"""
    await owned(db, job)
    await db.execute(
        text("""
                UPDATE ai_index_job SET status=:status,error_code=:error,
                finished_at=UTC_TIMESTAMP(6),claim_token=NULL,claim_until=NULL,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id
            """),
        {"id": job.id, "status": "FAILED" if error else "SUCCEEDED", "error": error},
    )
