"""装配AI的HTTP入口、配置与错误边界，不在导入时启动任务。"""

from fastapi import FastAPI

from servicedesk_ai.api.errors import register_error_handlers
from servicedesk_ai.api.health import router as health_router
from servicedesk_ai.api.trace import RequestTraceMiddleware
from servicedesk_ai.bootstrap.settings import Settings, get_settings


def create_app(settings: Settings | None = None) -> FastAPI:
    """按真实环境装配HTTP应用，生产关闭公开文档。"""
    settings = settings or get_settings()
    development = settings.environment == "dev"
    application = FastAPI(
        title="Service Desk AI",
        docs_url="/docs" if development else None,
        redoc_url=None,
        openapi_url="/openapi.json" if development else None,
    )
    application.add_middleware(RequestTraceMiddleware)
    application.include_router(health_router)
    register_error_handlers(application)
    return application


app = create_app()
