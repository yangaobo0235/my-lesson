"""数据库持久任务worker进程，索引与问答使用独立受限并发。作者：yangaobo。"""

import asyncio

from servicedesk_ai.bootstrap.resources import open_resources
from servicedesk_ai.bootstrap.settings import get_settings
from servicedesk_ai.diagnosis.execution import GenerationWorker
from servicedesk_ai.infrastructure.domain_rpc import open_domain_rpc
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas
from servicedesk_ai.infrastructure.mineru import MineruClient
from servicedesk_ai.services.evidence import EvidenceService
from servicedesk_ai.services.indexing import IndexWorker


async def index_loop(worker: IndexWorker) -> None:
    """空队列有限等待，不建立第二套持久Scheduler。"""
    while True:
        if not await worker.run_one():
            await asyncio.sleep(1)


async def parser_reconciliation_loop(worker: IndexWorker) -> None:
    """终态回收与业务执行分离，重启后继续核对原解析身份。"""
    while True:
        await worker.reconcile_parsers()
        await asyncio.sleep(10)


async def serve() -> None:
    """每个进程拥有并关闭连接；worker不运行Alembic。"""
    settings = get_settings()
    async with open_resources(settings) as resources, open_domain_rpc(settings.rpc) as rpc:
        replicas = IndexReplicas(
            resources.elasticsearch,
            resources.qdrant,
            resources.model,
            settings.elasticsearch.index,
            settings.qdrant.collection,
            settings.model,
        )
        tracer = resources.tracer_provider.get_tracer("servicedesk_ai.worker")
        index = IndexWorker(
            resources.database, rpc, MineruClient(resources.mineru), replicas, tracer
        )
        generation = GenerationWorker(
            resources.database,
            rpc,
            replicas,
            resources.model,
            settings,
            EvidenceService(resources.database, rpc),
            tracer,
        )
        async with asyncio.TaskGroup() as group:
            group.create_task(parser_reconciliation_loop(index))
            for _ in range(settings.execution.index_concurrency):
                group.create_task(index_loop(index))
            for _ in range(settings.execution.generation_concurrency):
                group.create_task(generation_loop(generation))


async def generation_loop(worker: GenerationWorker) -> None:
    """问答和索引分别限并发；不重新付费执行中断问答。"""
    while True:
        if not await worker.run_one():
            await asyncio.sleep(1)


def main() -> None:
    """独立执行入口，信号退出交给结构化任务作用域收集并释放资源。"""
    try:
        asyncio.run(serve())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
