"""RocketMQ独立接收进程：验证信封、短事务提交后ACK，不执行解析。作者：yangaobo。"""

import asyncio
import logging
import os
import re
from concurrent.futures import ThreadPoolExecutor

from opentelemetry.trace import SpanKind
from rocketmq import ClientConfiguration, Credentials, FilterExpression, SimpleConsumer

from servicedesk_ai.bootstrap.resources import open_resources
from servicedesk_ai.bootstrap.settings import get_settings
from servicedesk_ai.infrastructure.tracing import parent
from servicedesk_ai.schemas.indexing import IndexCommand
from servicedesk_ai.services.indexing import IndexService

LOGGER = logging.getLogger("servicedesk_ai.receiver")


def configure_direct_transport() -> None:
    """独立receiver清除进程继承的代理，覆盖SDK未暴露的Channel选项。

    RocketMQ SDK内部自行创建连接及路由后的通道，不能注入单通道配置。此设置仅
    作用于本进程并在创建SDK前执行，不修改用户系统代理、不替换SDK函数或TLS。
    同时清理gRPC的HTTPS/HTTP回退变量；Windows空变量会被原生运行时视为删除，
    不能仅设空grpc_proxy后仍让SDK回退到其他代理。其他应用进程保持原环境。
    """
    for name in (
        "grpc_proxy",
        "https_proxy",
        "http_proxy",
        "GRPC_PROXY",
        "HTTPS_PROXY",
        "HTTP_PROXY",
    ):
        os.environ.pop(name, None)


async def serve() -> None:
    """同步SDK位于专属有界线程，AsyncConnection始终留在当前事件循环。"""
    configure_direct_transport()
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
    async with open_resources(settings, process="receiver") as resources:
        service = IndexService(resources.database)
        try:
            await loop.run_in_executor(executor, consumer.startup)
            LOGGER.info("索引接收器已启动", extra={"event": "receiver_ready"})
            while True:
                message_id = "unknown"
                delivery_attempt: int | None = None
                try:
                    messages = await loop.run_in_executor(
                        executor, consumer.receive, 1, mq.invisible_seconds
                    )
                    for message in messages or []:
                        if isinstance(message.message_id, str) and re.fullmatch(
                            r"[0-9A-Fa-f]{32,64}", message.message_id
                        ):
                            message_id = message.message_id
                        attempt = message.delivery_attempt
                        if type(attempt) is int and 1 <= attempt <= 10000:
                            delivery_attempt = attempt
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
                    LOGGER.warning(
                        "索引消息未确认 messageId=%s deliveryAttempt=%s errorType=%s",
                        message_id,
                        delivery_attempt,
                        type(error).__name__,
                        extra={"event": "index_message_unacknowledged"},
                    )
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
