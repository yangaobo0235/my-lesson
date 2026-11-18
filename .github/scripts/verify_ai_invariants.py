"""在迁移脚本创建的独占CI库验证索引乱序、数据库执行权和费用未知。作者：yangaobo。"""

import asyncio
import hashlib
import os
from collections.abc import Awaitable
from uuid import uuid4

from sqlalchemy import URL, text
from sqlalchemy.ext.asyncio import AsyncConnection, create_async_engine

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import evidence, index_execution, index_jobs, runs
from servicedesk_ai.schemas.indexing import (
    IndexCommand,
    ProcessingConfig,
    canonical_hash,
)
from servicedesk_ai.schemas.model_calls import ModelInputManifest, PricingSnapshot
from servicedesk_ai.schemas.runs import RunContext, RunPipeline
from servicedesk_ai.services.parsing import build_manifest, parse_markdown


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


async def verify_run(db: AsyncConnection) -> int:
    """真实Run租约失联收敛UNKNOWN；已取消调用保持未发送零计量，不自动重新排队。"""
    conversation, owner, run_id, message = (identifier() for _ in range(4))
    content = "解释网络连接"
    digest = hashlib.sha256(content.encode()).hexdigest()
    context = RunContext(
        mode="CONSULT",
        customer_id=None,
        service_item_id=None,
        device_id=None,
        product_model_id=None,
        ticket_id=None,
        support_context=None,
        messages=[{"id": message, "content_sha256": digest}],
    )
    pipeline = RunPipeline(prompt_version="ci", tool_version="ci", model="ci")
    await db.execute(
        text("""
        INSERT INTO ai_conversation(id,owner_id,mode,client_request_id,request_hash,
            title,message_sequence)
        VALUES (:id,:owner,'CONSULT',:key,:hash,'执行权验收',1)
    """),
        {"id": conversation, "owner": owner, "key": identifier(), "hash": "a" * 64},
    )
    await db.execute(
        text("""
        INSERT INTO ai_message(id,conversation_id,sequence_number,role,content,content_sha256,
            client_request_id,request_hash)
        VALUES (:id,:conversation,1,'USER',:content,:sha,:key,:hash)
    """),
        {
            "id": message,
            "conversation": conversation,
            "content": content,
            "sha": digest,
            "key": identifier(),
            "hash": digest,
        },
    )
    await db.execute(
        text("""
        INSERT INTO ai_run(id,conversation_id,owner_id,input_message_id,client_request_id,
            request_hash,
            session_ref,auth_version,pipeline_config,context_manifest,deadline_at)
        VALUES (:id,:conversation,:owner,:message,:key,:hash,:session,1,:pipeline,:context,
            UTC_TIMESTAMP(6)+INTERVAL 180 SECOND)
    """),
        {
            "id": run_id,
            "conversation": conversation,
            "owner": owner,
            "message": message,
            "key": identifier(),
            "hash": "a" * 64,
            "session": str(uuid4()),
            "pipeline": pipeline.model_dump_json(),
            "context": context.model_dump_json(),
        },
    )
    run = await runs.claim(db)
    assert run is not None and run.id == run_id
    await runs.owned(db, run)
    calls = [identifier(), identifier()]
    manifest = ModelInputManifest(
        request_sha256="a" * 64,
        estimator_version="ci",
        input_tokens_upper=10,
        output_tokens_limit=5,
    )
    for number, call_id in enumerate(calls, 1):
        await db.execute(
            text("""
            INSERT INTO ai_model_call(id,run_id,call_number,purpose,status,claim_token,
                provider,requested_model,input_manifest,reserved_tokens,pricing_snapshot,started_at)
            VALUES(:id,:run,:number,'CHAT',:status,:token,'CI','ci',:manifest,15,:pricing,:started)
        """),
            {
                "id": call_id,
                "run": run_id,
                "number": number,
                "status": "IN_FLIGHT" if number == 1 else "PREPARED",
                "token": run.claim_token,
                "manifest": manifest.model_dump_json(),
                "pricing": PricingSnapshot().model_dump_json(),
                "started": run.started_at if number == 1 else None,
            },
        )
    await db.execute(
        text("""
        UPDATE ai_run SET claim_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=:id
    """),
        {"id": run_id},
    )
    assert not await runs.renew(db, run)
    await conflict(runs.owned(db, run))
    assert await runs.claim(db) is None
    interrupted = await runs.run(db, run_id)
    assert interrupted.status == "INTERRUPTED" and interrupted.claim_token is None
    await conflict(runs.owned(db, run))
    row = (
        await db.execute(
            text("SELECT status,input_tokens,output_tokens FROM ai_model_call WHERE id=:id"),
            {"id": calls[0]},
        )
    ).one()
    assert tuple(row) == ("UNKNOWN", None, None)
    row = (
        await db.execute(
            text("SELECT status,input_tokens,output_tokens FROM ai_model_call WHERE id=:id"),
            {"id": calls[1]},
        )
    ).one()
    assert tuple(row) == ("CANCELLED", 0, 0)
    assert await runs.claim(db) is None
    return 8


async def verify_sections(db: AsyncConnection) -> int:
    """真实MySQL固定父子清单并完整读回；正文定位和父身份不能在恢复时重算。"""
    job_id = identifier()
    await index_jobs.accept_command(db, command(job_id))
    job = await index_jobs.claim_job(db)
    assert job is not None and job.id == job_id
    original = (
        "# 网络检查\n" + "记录连接现象，不改配置。\n" * 100 + "停止条件：设备异常发热停止使用。\n"
    ).encode()
    leaves = parse_markdown(original, job_id, job.processing_config)
    manifest = build_manifest(leaves, job_id, job.processing_config)
    await index_jobs.save_manifest(db, job, manifest, None)
    assert await index_jobs.read_chunks(db, job_id) == manifest
    assert await index_execution.chunk_count(db, {"id": job_id}) == len(leaves)
    first = await evidence.readable_chunk(db, {"id": manifest[1].id})
    assert first is not None and first["parent_chunk_id"] == manifest[0].id
    parent = await evidence.readable_chunk(db, {"id": first["parent_chunk_id"]})
    assert parent is not None and "停止条件" in parent["content"]
    parts = await evidence.adjacent_sections(db, manifest[0].id)
    assert [part["id"] for part in parts] == [manifest[0].id]
    await conflict(index_jobs.save_manifest(db, job, manifest, None))
    await index_jobs.finish_job(db, job)
    return 6


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
                    "run_invariants": await verify_run(db),
                    "evidence_invariants": await verify_sections(db),
                }
            finally:
                await transaction.rollback()
    finally:
        await engine.dispose()


def verify_ai_invariants(connection: dict) -> dict[str, int]:
    """向正式数据库验收入口提供同步调用，不创建第二套数据库配置。"""
    return asyncio.run(verify(connection))
