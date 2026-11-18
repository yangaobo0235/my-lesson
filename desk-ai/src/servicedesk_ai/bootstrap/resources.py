"""按API进程生命周期装配外部连接；不自动迁移、建索引或消费消息。"""

import asyncio
import logging
import ssl
from collections.abc import AsyncIterator
from contextlib import AsyncExitStack, asynccontextmanager
from dataclasses import dataclass
from typing import Literal

import httpx
from opentelemetry.sdk.trace import TracerProvider
from redis.asyncio import Redis
from sqlalchemy import URL
from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine

from servicedesk_ai.bootstrap.components import HttpSettings
from servicedesk_ai.bootstrap.observability import open_observability
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.infrastructure.models import ModelClient


@dataclass(frozen=True)
class Resources:
    """仅保存本进程拥有的连接；业务用例按明确依赖取得客户端。"""

    database: AsyncEngine
    redis: Redis
    elasticsearch: httpx.AsyncClient
    qdrant: httpx.AsyncClient
    mineru: httpx.AsyncClient
    model: ModelClient
    tracer_provider: TracerProvider


async def close_observability(provider: TracerProvider, handler: logging.Handler) -> None:
    """从事件循环移出有界追踪冲刷，再释放当前文件句柄。"""
    try:
        await asyncio.to_thread(provider.shutdown)
    finally:
        logging.getLogger("servicedesk_ai").removeHandler(handler)
        await asyncio.to_thread(handler.close)


def http_client(
    settings: HttpSettings,
    headers: dict[str, str] | None = None,
    auth: httpx.BasicAuth | None = None,
) -> httpx.AsyncClient:
    """构造有界同源客户端，禁止重定向和隐式代理。"""
    return httpx.AsyncClient(
        base_url=settings.url,
        headers=headers,
        auth=auth,
        timeout=httpx.Timeout(settings.timeout_seconds, connect=2),
        limits=httpx.Limits(max_connections=5, max_keepalive_connections=5),
        follow_redirects=False,
        trust_env=False,
        verify=ssl.create_default_context(cafile=settings.ca_file),
    )


@asynccontextmanager
async def open_resources(
    settings: Settings, *, process: Literal["api", "worker", "receiver"] = "api"
) -> AsyncIterator[Resources]:
    """在同一事件循环创建和逆序关闭资源，不执行DDL或健康付费调用。"""
    async with AsyncExitStack() as stack:
        provider, handler = open_observability(settings, process)
        stack.push_async_callback(close_observability, provider, handler)
        mysql = settings.mysql
        engine = create_async_engine(
            URL.create(
                "mysql+asyncmy",
                username=mysql.username,
                password=mysql.password.get_secret_value(),
                host=mysql.host,
                port=mysql.port,
                database=mysql.database,
            ),
            pool_size=mysql.pool_size,
            max_overflow=0,
            pool_recycle=45,
            pool_pre_ping=True,
            pool_timeout=3,
            isolation_level="READ COMMITTED",
            hide_parameters=True,
            connect_args={
                "connect_timeout": 2,
                "read_timeout": 5,
                "charset": "utf8mb4",
                "init_command": "SET time_zone = '+00:00', innodb_lock_wait_timeout = 2, "
                "wait_timeout = 60",
            },
        )
        stack.push_async_callback(engine.dispose)
        redis_settings = settings.redis
        redis = Redis(
            host=redis_settings.host,
            port=redis_settings.port,
            username=redis_settings.username,
            password=redis_settings.password.get_secret_value(),
            db=redis_settings.database,
            socket_connect_timeout=2,
            socket_timeout=2,
            max_connections=5,
            decode_responses=True,
        )
        stack.push_async_callback(redis.aclose)
        es = settings.elasticsearch
        elasticsearch = await stack.enter_async_context(
            http_client(es, auth=httpx.BasicAuth(es.username, es.password.get_secret_value()))
        )
        qdrant = await stack.enter_async_context(
            http_client(settings.qdrant, {"api-key": settings.qdrant.api_key.get_secret_value()})
        )
        mineru_key = settings.mineru.api_key.get_secret_value()
        mineru = await stack.enter_async_context(
            http_client(
                settings.mineru, {"Authorization": f"Bearer {mineru_key}"} if mineru_key else {}
            )
        )
        model = ModelClient(settings.model, tracer=provider.get_tracer("servicedesk_ai.models"))
        stack.push_async_callback(model.close)
        yield Resources(engine, redis, elasticsearch, qdrant, mineru, model, provider)
