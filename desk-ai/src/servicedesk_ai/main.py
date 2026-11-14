"""装配AI的HTTP入口、配置与错误边界，不在导入时启动任务。"""

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from servicedesk_ai.api.diagnosis import admin_router
from servicedesk_ai.api.diagnosis import router as diagnosis_router
from servicedesk_ai.api.errors import register_error_handlers
from servicedesk_ai.api.health import router as health_router
from servicedesk_ai.api.trace import RequestTraceMiddleware
from servicedesk_ai.bootstrap.settings import Settings, get_settings


def create_app(settings: Settings | None = None) -> FastAPI:
    """按真实环境装配HTTP应用，生产关闭公开文档。"""
    settings = settings or get_settings()
    development = settings.environment == "dev"

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        """正常启动先迁移再创建资源；隔离测试显式关闭外部资源。"""
        if settings.infrastructure_enabled:
            from servicedesk_ai.bootstrap.application import services
            from servicedesk_ai.bootstrap.migrations import migrate_database
            from servicedesk_ai.bootstrap.resources import open_resources
            from servicedesk_ai.infrastructure.domain_rpc import open_domain_rpc
            from servicedesk_ai.rpc.server import open_rpc_server

            if settings.migrations_enabled:
                await asyncio.to_thread(migrate_database, settings)
            async with open_resources(settings) as resources, open_domain_rpc(settings.rpc) as rpc:
                app.state.resources = resources
                application_services = services(resources, rpc, settings)
                app.state.services = application_services
                try:
                    async with open_rpc_server(application_services, settings.rpc):
                        yield
                finally:
                    app.state.resources = None
                    app.state.services = None
        else:
            yield

    application = FastAPI(
        title="Service Desk AI",
        lifespan=lifespan,
        docs_url="/docs" if development else None,
        redoc_url=None,
        openapi_url="/openapi.json" if development else None,
    )
    application.add_middleware(RequestTraceMiddleware)
    application.include_router(health_router)
    application.include_router(diagnosis_router)
    application.include_router(admin_router)
    register_error_handlers(application)
    return application
