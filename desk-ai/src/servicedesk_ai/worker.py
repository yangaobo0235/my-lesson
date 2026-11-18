"""数据库持久任务worker进程，索引与问答使用独立受限并发。作者：yangaobo。"""

import asyncio
import logging
from collections.abc import Awaitable, Callable

from sqlalchemy.exc import InterfaceError, OperationalError

from servicedesk_ai.bootstrap.application import services
from servicedesk_ai.bootstrap.resources import open_resources
from servicedesk_ai.bootstrap.settings import get_settings
from servicedesk_ai.diagnosis.execution import RunWorker
from servicedesk_ai.infrastructure.domain_rpc import open_domain_rpc
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas
from servicedesk_ai.infrastructure.mineru import MineruClient
from servicedesk_ai.services.evidence import EvidenceService
from servicedesk_ai.services.indexing import IndexWorker
from servicedesk_ai.services.model_calls import ModelCalls
from servicedesk_ai.services.run_tools import RunTools
from servicedesk_ai.services.task_maintenance import TaskMaintenance

LOGGER = logging.getLogger("servicedesk_ai.worker")


async def poll(operation: Callable[[], Awaitable[bool | None]], name: str, interval: float) -> None:
    """临时数据库不可达时退避再领取；未知调用仍由原租约和账目收敛，不自动重付费。"""
    delay = 1.0
    while True:
        try:
            worked = await operation()
        except (OperationalError, InterfaceError) as error:
            LOGGER.warning(
                "持久队列暂不可达 loop=%s errorType=%s retrySeconds=%s",
                name,
                type(error).__name__,
                delay,
            )
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30)
        else:
            delay = 1.0
            if not worked:
                await asyncio.sleep(interval)


async def maintain_tasks(maintenance: TaskMaintenance) -> None:
    """维护持久待答期限及正式动作回执，不重新运行模型。"""
    await maintenance.expire_checks()
    await maintenance.reconcile_drafts()


async def serve() -> None:
    """每个进程拥有并关闭连接；worker不运行Alembic。"""
    settings = get_settings()
    async with (
        open_resources(settings, process="worker") as resources,
        open_domain_rpc(settings.rpc) as rpc,
    ):
        replicas = IndexReplicas(
            resources.elasticsearch,
            resources.qdrant,
            settings.elasticsearch.index,
            settings.qdrant.collection,
            settings.model,
        )
        tracer = resources.tracer_provider.get_tracer("servicedesk_ai.worker")
        models = ModelCalls(
            resources.database, resources.model, settings.chat_pricing, settings.embedding_pricing
        )
        index = IndexWorker(
            resources.database, rpc, MineruClient(resources.mineru), replicas, models, tracer
        )
        evidence = EvidenceService(resources.database, rpc)
        run_worker = RunWorker(
            resources.database,
            rpc,
            settings,
            evidence,
            models,
            RunTools(resources.database, rpc, replicas, evidence, models),
            tracer,
        )
        LOGGER.info("持久任务执行器已启动", extra={"event": "worker_ready"})
        async with asyncio.TaskGroup() as group:
            maintenance = TaskMaintenance(
                resources.database, services(resources, rpc, settings).drafts
            )
            group.create_task(poll(lambda: maintain_tasks(maintenance), "task-maintenance", 30))
            group.create_task(poll(index.reconcile_parsers, "parser-reconciliation", 10))
            for _ in range(settings.execution.index_concurrency):
                group.create_task(poll(index.run_one, "index", 1))
            for _ in range(settings.execution.run_concurrency):
                group.create_task(poll(run_worker.run_one, "run", 1))


def main() -> None:
    """独立执行入口，信号退出交给结构化任务作用域收集并释放资源。"""
    try:
        asyncio.run(serve())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
