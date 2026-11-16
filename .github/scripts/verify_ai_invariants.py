"""在迁移脚本创建的独占CI库验证索引乱序、数据库执行权和费用未知。作者：yangaobo。"""

import asyncio
import json
import os
from collections.abc import Awaitable
from uuid import uuid4

from sqlalchemy import URL, text
from sqlalchemy.ext.asyncio import AsyncConnection, create_async_engine

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import conversation_queries, generation_execution, index_jobs
from servicedesk_ai.repositories import diagnosis as runs
from servicedesk_ai.schemas.execution import GenerationContext, GenerationPipeline
from servicedesk_ai.schemas.indexing import IndexCommand, ProcessingConfig, canonical_hash


def identifier() -> str:
    """每次验收使用独立业务身份，不依赖历史夹具。"""
    return uuid4().hex


def command(job: str, action: str = "SUBMIT", digest: str = "a" * 64) -> IndexCommand:
    """生成校验摘要的真实信封；取消只改变动作和操作身份。"""
    source = {
        "schemaVersion": 2,
        "jobId": job,
        "documentId": job,
        "attemptNumber": 1,
        "contentFormat": "MARKDOWN",
        "fileSha256": digest,
        "requestedBy": "c" * 32,
        "processingConfig": ProcessingConfig().model_dump(by_alias=True),
    }
    return IndexCommand.model_validate(
        source
        | {
            "requestHash": canonical_hash(source),
            "command": action,
            "operationId": job if action == "SUBMIT" else identifier(),
        }
    )


async def conflict(operation: Awaitable[object]) -> None:
    """断言实际仓储拒绝写入，而不是以替身替代执行权SQL。"""
    try:
        await operation
    except BusinessError as error:
        assert error.error == CommonErrorCode.CONFLICT
    else:
        raise AssertionError("旧执行权或异义输入未被拒绝")


async def verify_index(db: AsyncConnection) -> int:
    """取消先到与重复投递不复活；过期及旧领取者不得提交阶段或终态。"""
    tombstone = identifier()
    for action in ("CANCEL", "SUBMIT", "CANCEL", "SUBMIT"):
        await index_jobs.accept_command(db, command(tombstone, action))
    cancelled = await index_jobs.get_job(db, tombstone)
    assert cancelled is not None and cancelled.status == "CANCELLED"
    assert await index_jobs.claim_job(db) is None
    await conflict(index_jobs.accept_command(db, command(tombstone, digest="d" * 64)))

    job_id = identifier()
    await index_jobs.accept_command(db, command(job_id))
    await index_jobs.accept_command(db, command(job_id))
    old = await index_jobs.claim_job(db)
    assert old is not None and old.id == job_id
    await db.execute(
        text("""
            UPDATE ai_index_job SET created_at=UTC_TIMESTAMP(6)-INTERVAL 3 SECOND,
            started_at=UTC_TIMESTAMP(6)-INTERVAL 2 SECOND,
            claim_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=:id
        """),
        {"id": job_id},
    )
    assert not await index_jobs.renew_job(db, old)
    await conflict(index_jobs.finish_job(db, old))
    new = await index_jobs.claim_job(db)
    assert new is not None and new.id == old.id and new.claim_token != old.claim_token
    await conflict(index_jobs.save_manifest(db, old, [], None))
    await conflict(index_jobs.finish_job(db, old))
    assert not await index_jobs.renew_job(db, old)
    assert await index_jobs.renew_job(db, new)
    await index_jobs.finish_job(db, new)
    finished = await index_jobs.get_job(db, job_id)
    assert finished is not None and finished.status == "SUCCEEDED"
    return 10


async def verify_generation(db: AsyncConnection) -> int:
    """过期运行中断而不自动付费重跑，迟到模型回执不能改写UNKNOWN。"""
    conversation, owner, customer, service, run_id = (identifier() for _ in range(5))
    context = GenerationContext(
        schema_version=1,
        mode="CUSTOMER_SELF_SERVICE",
        customer_id=customer,
        service_item_id=service,
        device_id=None,
        product_model_id=None,
        ticket_id=None,
        environment={},
        ticket_title="",
        ticket_symptom="",
    )
    pipeline = GenerationPipeline(
        schema_version=1, prompt_version="ci", tool_version="ci", model="ci"
    )
    await conversation_queries.insert_conversation(
        db,
        {
            "id": conversation,
            "owner": owner,
            "customer": customer,
            "mode": "CUSTOMER_SELF_SERVICE",
            "ticket": None,
            "service": service,
            "device": None,
            "model": None,
            "context": json.dumps({"schema_version": 1, **context.environment.model_dump()}),
            "title": "数据库执行权验收",
        },
    )
    await conversation_queries.insert_generation(
        db,
        {
            "id": run_id,
            "conversation": conversation,
            "owner": owner,
            "round": 1,
            "version": 1,
            "ticket_version": None,
            "question": "办公网络无法连接",
            "key": identifier(),
            "hash": "a" * 64,
            "session": str(uuid4()),
            "auth": 1,
            "pipeline": pipeline.model_dump_json(),
            "context": context.model_dump_json(),
            "trace": None,
        },
    )
    run = await runs.claim(db)
    assert run is not None and run.id == run_id
    await runs.owned(db, run)
    call_id = identifier()
    await generation_execution.insert_model_call(
        db,
        {
            "id": call_id,
            "run": run_id,
            "number": 1,
            "model": "ci",
            "manifest": json.dumps({"schema_version": 1}),
        },
    )
    await db.execute(
        text("""
            UPDATE ai_generation SET created_at=UTC_TIMESTAMP(6)-INTERVAL 3 SECOND,
            started_at=UTC_TIMESTAMP(6)-INTERVAL 2 SECOND,
            claim_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=:id
        """),
        {"id": run_id},
    )
    assert not await runs.renew(db, run)
    await conflict(runs.owned(db, run))
    assert await runs.claim(db) is None
    interrupted = await runs.generation(db, run_id)
    assert interrupted.status == "INTERRUPTED" and interrupted.claim_token is None
    await conflict(runs.owned(db, run))
    await generation_execution.complete_model_call(
        db,
        {"id": call_id, "model": "ci", "input": 12, "output": 3, "cached": 0},
    )
    row = (
        await db.execute(
            text("SELECT status,input_tokens,output_tokens FROM ai_model_call WHERE id=:id"),
            {"id": call_id},
        )
    ).one()
    assert tuple(row) == ("UNKNOWN", None, None)
    assert await runs.claim(db) is None
    return 7


async def verify(connection: dict) -> dict[str, int]:
    """只访问外层脚本刚创建的固定CI库；所有业务夹具随事务回滚。"""
    if os.environ.get("DESK_VERIFY_ISOLATED") != "true":
        raise RuntimeError("必须由独占实例迁移验收入口调用")
    engine = create_async_engine(
        URL.create(
            "mysql+asyncmy",
            username=connection["user"],
            password=connection["password"],
            host=connection["host"],
            port=connection["port"],
            database="desk_ai_ci",
        ),
        isolation_level="READ COMMITTED",
        hide_parameters=True,
        connect_args={
            "connect_timeout": 2,
            "read_timeout": 5,
            "charset": "utf8mb4",
            "init_command": "SET time_zone = '+00:00', innodb_lock_wait_timeout = 2",
        },
    )
    try:
        async with engine.connect() as db:
            transaction = await db.begin()
            try:
                return {
                    "index_invariants": await verify_index(db),
                    "generation_invariants": await verify_generation(db),
                }
            finally:
                await transaction.rollback()
    finally:
        await engine.dispose()


def verify_ai_invariants(connection: dict) -> dict[str, int]:
    """向正式数据库验收入口提供同步调用，不创建第二套数据库配置。"""
    return asyncio.run(verify(connection))
