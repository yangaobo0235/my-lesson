"""RocketMQ独立接收进程：验证信封、短事务提交后ACK，不执行解析。作者：yangaobo。"""

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor

from opentelemetry.trace import SpanKind
from rocketmq import ClientConfiguration, Credentials, FilterExpression, SimpleConsumer

from servicedesk_ai.bootstrap.resources import open_resources
from servicedesk_ai.bootstrap.settings import get_settings
from servicedesk_ai.infrastructure.tracing import parent
from servicedesk_ai.schemas.indexing import IndexCommand
from servicedesk_ai.services.indexing import IndexService

LOGGER = logging.getLogger(__name__)


async def serve() -> None:
    """同步SDK位于专属有界线程，AsyncConnection始终留在当前事件循环。"""
    settings = get_settings()
    mq = settings.rocketmq
    config = ClientConfiguration(
        mq.endpoints,
        Credentials(mq.access_key.get_secret_value(), mq.secret_key.get_secret_value()),
        request_timeout=3,
    )
    consumer = SimpleConsumer(
        config,
        mq.consumer_group,
        {mq.topic: FilterExpression()},
        await_duration=5,
        tls_enable=mq.tls_enabled,
    )
    loop = asyncio.get_running_loop()
    executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="index-receiver")
    async with open_resources(settings) as resources:
        service = IndexService(resources.database)
        try:
            await loop.run_in_executor(executor, consumer.startup)
            while True:
                try:
                    messages = await loop.run_in_executor(
                        executor, consumer.receive, 1, mq.invisible_seconds
                    )
                    for message in messages or []:
                        if len(message.body) > 65536:
                            raise ValueError("索引信封超过预算")
                        command = IndexCommand.model_validate_json(message.body)
                        with resources.tracer_provider.get_tracer(__name__).start_as_current_span(
                            "ai.index.receive",
                            context=parent(message.properties),
                            kind=SpanKind.CONSUMER,
                            record_exception=False,
                            set_status_on_exception=False,
                        ):
                            async with asyncio.timeout(mq.acceptance_timeout_seconds):
                                await service.accept(command)
                        # 唯一允许ACK的位置：服务事务已经提交。丢ACK由原任务身份重投收敛。
                        await loop.run_in_executor(executor, consumer.ack, message)
                except asyncio.CancelledError:
                    raise
                except Exception as error:
                    LOGGER.warning("索引消息未确认 errorType=%s", type(error).__name__)
                    await asyncio.sleep(1)
        finally:
            await loop.run_in_executor(executor, consumer.shutdown)
            executor.shutdown(wait=True, cancel_futures=True)


def main() -> None:
    """独立receiver不发起结构迁移，要求API先完成迁移。"""
    try:
        asyncio.run(serve())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
