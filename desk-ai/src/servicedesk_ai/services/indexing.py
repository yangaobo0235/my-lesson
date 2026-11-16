"""可靠索引短事务受理与持久worker编排；网络调用不持有MySQL事务。作者：yangaobo。"""

from __future__ import annotations

import asyncio
import logging

import grpc
import httpx
from opentelemetry.trace import NoOpTracer, SpanKind, Tracer
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncEngine

from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas
from servicedesk_ai.infrastructure.mineru import MineruClient, parse_artifact
from servicedesk_ai.infrastructure.rpc_contract import request_budget
from servicedesk_ai.infrastructure.tracing import parent, snapshot
from servicedesk_ai.repositories import index_execution as queries
from servicedesk_ai.repositories import index_jobs as jobs
from servicedesk_ai.schemas.indexing import IndexCommand, ParsedChunk
from servicedesk_ai.services.parsing import parse_markdown

LOGGER = logging.getLogger(__name__)


class IndexService:
    """接收与执行共用用例；API仅开放状态查询。"""

    def __init__(self, engine: AsyncEngine) -> None:
        """数据库连接池归进程资源所有者。"""
        self.engine = engine

    async def accept(self, command: IndexCommand) -> None:
        """唯一键竞争回滚后再核对同一身份，不在失败事务里继续ACK。"""
        try:
            async with self.engine.begin() as db:
                await jobs.accept_command(db, command, snapshot())
        except IntegrityError:
            async with self.engine.begin() as db:
                await jobs.accept_command(db, command, snapshot())

    async def get_status(self, job_id: str) -> tuple[jobs.IndexJob | None, int]:
        """任务未见只报告NOT_FOUND，不推断Broker是否在途。"""
        async with self.engine.connect() as db:
            job = await jobs.get_job(db, job_id)
            count = await queries.chunk_count(db, {"id": job_id})
            return job, count


class IndexWorker:
    """按原任务恢复阶段；租约丢失后外部回执不能写终态。"""

    def __init__(
        self,
        engine: AsyncEngine,
        rpc: DomainRpc,
        mineru: MineruClient,
        replicas: IndexReplicas,
        tracer: Tracer | None = None,
    ) -> None:
        """每个worker拥有资源，不与receiver跨事件循环共享。"""
        self.engine, self.rpc, self.mineru, self.replicas = engine, rpc, mineru, replicas
        self.tracer = tracer or NoOpTracer()

    async def heartbeat(self, job: jobs.IndexJob) -> None:
        """续约只保护数据库执行权，不宣称远端已停止。"""
        while True:
            await asyncio.sleep(5)
            async with self.engine.begin() as db:
                if not await jobs.renew_job(db, job):
                    raise RuntimeError("索引执行权失效")

    async def run_one(self) -> bool:
        """领取一个任务；异常持久失败，未知解析创建保留对账状态。"""
        async with self.engine.begin() as db:
            job = await jobs.claim_job(db)
        if job is None:
            return False
        with self.tracer.start_as_current_span(
            "ai.index.worker",
            context=parent(job.trace_context),
            kind=SpanKind.CONSUMER,
            record_exception=False,
            set_status_on_exception=False,
        ):
            return await self.run_claimed(job)

    async def run_claimed(self, job: jobs.IndexJob) -> bool:
        """当前领取执行的网络等待与结果CAS共享同一有界任务作用域。"""
        heartbeat = asyncio.create_task(self.heartbeat(job))
        with request_budget(job.processing_config.total_seconds):
            execution = asyncio.create_task(self.execute(job))
        try:
            done, _ = await asyncio.wait(
                {heartbeat, execution}, return_when=asyncio.FIRST_COMPLETED
            )
            for task in done:
                await task
        except asyncio.CancelledError:
            raise
        except Exception as error:
            LOGGER.warning("索引执行失败 jobId=%s errorType=%s", job.id, type(error).__name__)
            if isinstance(error, grpc.aio.AioRpcError):
                LOGGER.warning("索引原件RPC失败 jobId=%s rpcStatus=%s", job.id, error.code().name)
            async with self.engine.begin() as db:
                current = await jobs.get_job(db, job.id, lock=True)
                if (
                    current
                    and current.status == "RUNNING"
                    and current.claim_token == job.claim_token
                ):
                    await jobs.finish_job(db, job, "INDEX_DEPENDENCY_FAILED")
        finally:
            for task in (heartbeat, execution):
                task.cancel()
            await asyncio.gather(heartbeat, execution, return_exceptions=True)
        return True

    async def execute(self, job: jobs.IndexJob) -> None:
        """解析清单只固定一次，恢复外部副本不重新解析或更换身份。"""
        async with asyncio.timeout(job.processing_config.total_seconds):
            async with self.engine.connect() as db:
                chunks = await jobs.read_chunks(db, job.id)
            if not chunks:
                data = await self.rpc.read_original(
                    job.document_id, job.id, job.file_sha256, job.content_format
                )
                pages = None
                if job.content_format == "MARKDOWN":
                    chunks = parse_markdown(data, job.id, job.processing_config)
                else:
                    chunks, pages = await self.parse_pdf(job, data)
                async with self.engine.begin() as db:
                    await jobs.save_manifest(db, job, chunks, pages)
            async with self.engine.begin() as db:
                await jobs.owned(db, job)
                await queries.mark_indexing(db, {"id": job.id})
            await self.replicas.prepare()
            await self.replicas.write_and_verify(job.id, job.document_id, chunks)
            async with self.engine.begin() as db:
                await jobs.finish_job(db, job)

    async def parse_pdf(self, job: jobs.IndexJob, data: bytes) -> tuple[list[ParsedChunk], int]:
        """已知远端身份沿原ID查询；未知创建不另开任务。"""
        async with self.engine.begin() as db:
            await jobs.owned(db, job)
            current = await jobs.get_job(db, job.id)
            if current is None:
                raise RuntimeError("索引任务已不存在")
            unknown = current.parser_state in {"SUBMITTING", "UNKNOWN"}
            if unknown:
                await queries.mark_parser_unknown(db, {"id": job.id})
            create = current.parser_state == "NOT_SUBMITTED"
            if create:
                await queries.mark_parser_submitting(db, {"id": job.id})
        if unknown:
            raise ValueError("原解析创建结果未知，需原身份对账")
        task_id, file_id = current.parser_task_id, current.parser_file_id
        if create:
            try:
                task_id, file_id = await self.mineru.create(
                    len(data), job.file_sha256, job.processing_config
                )
            except Exception:
                async with self.engine.begin() as db:
                    await queries.mark_unconfirmed_parser_unknown(db, {"id": job.id})
                raise
            async with self.engine.begin() as db:
                # 即使刚被取消，也保存原远端身份用于回收，不改变本地终态。
                await queries.save_parser_identity(
                    db, {"id": job.id, "task": task_id, "file": file_id}
                )
        if not task_id or not file_id:
            raise ValueError("解析身份缺失")
        read_failures = 0
        upload_attempts = 0
        download_attempts = 0
        while True:
            try:
                state, url = await self.mineru.status(task_id, file_id)
                read_failures = 0
            except httpx.TransportError:
                read_failures += 1
                if read_failures >= 3:
                    raise
                await asyncio.sleep(2)
                continue
            async with self.engine.begin() as db:
                current = await jobs.get_job(db, job.id, lock=True)
                if current is None:
                    raise RuntimeError("索引任务已不存在")
                cancelled = current.last_command == "CANCEL"
                if state in {"completed", "failed", "canceled"}:
                    await queries.settle_parser(db, {"id": job.id})
            if cancelled:
                if state not in {"completed", "failed", "canceled"}:
                    await self.mineru.cancel(task_id)
                raise ValueError("索引已取消")
            if state == "waiting-file":
                upload_attempts += 1
                if upload_attempts > 2:
                    raise ValueError("原解析任务的文件上传未确认")
                try:
                    await self.mineru.upload(task_id, file_id, data)
                except httpx.TransportError:
                    # 上传可能已经受理；下一次先查原任务，不能直接另建或立刻再传。
                    pass
                except httpx.HTTPStatusError as error:
                    if error.response.status_code != 409 and error.response.status_code < 500:
                        raise
            elif state == "completed":
                if not url:
                    raise ValueError("解析缺少结果包")
                download_attempts += 1
                try:
                    artifact = await self.mineru.download(url)
                except httpx.TransportError:
                    if download_attempts >= 2:
                        raise
                    await asyncio.sleep(2)
                    continue
                return parse_artifact(artifact, job.id, job.processing_config)
            elif state in {"failed", "canceled"}:
                raise ValueError("远端解析失败")
            await asyncio.sleep(2)

    async def reconcile_parsers(self) -> None:
        """独立回收终态原任务；不领取执行权，不改变本地结果或重新创建。"""
        async with self.engine.connect() as db:
            rows = await queries.unsettled_parsers(db)
        for row in rows:
            try:
                async with asyncio.timeout(10):
                    state, _ = await self.mineru.status(
                        row["parser_task_id"], row["parser_file_id"]
                    )
                    if state not in {"completed", "failed", "canceled"}:
                        await self.mineru.cancel(row["parser_task_id"])
                        continue
                    async with self.engine.begin() as db:
                        await queries.settle_original_parser(
                            db,
                            {
                                "id": row["id"],
                                "task": row["parser_task_id"],
                                "file": row["parser_file_id"],
                            },
                        )
            except Exception as error:
                LOGGER.warning(
                    "解析回收待重试 jobId=%s errorType=%s", row["id"], type(error).__name__
                )
