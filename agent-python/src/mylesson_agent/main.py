from contextlib import asynccontextmanager, suppress
from typing import Any

from fastapi import FastAPI
from fastapi.responses import JSONResponse, PlainTextResponse
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from redis.exceptions import RedisError
from sqlalchemy import text

from mylesson_agent import __version__
from mylesson_agent.api import admin, business, conversations, ingestion, runs
from mylesson_agent.api.dependencies import conversation_service
from mylesson_agent.config import get_settings
from mylesson_agent.container import get_container
from mylesson_agent.observability.setup import configure_logging, configure_tracing

settings = get_settings()
configure_logging(settings)
configure_tracing(settings)


@asynccontextmanager
async def lifespan(_app: FastAPI) -> Any:
    yield
    await conversation_service().shutdown()
    await get_container().close()


app = FastAPI(
    title="MyLesson Python Agent",
    version=__version__,
    lifespan=lifespan,
)
app.include_router(conversations.router)
app.include_router(business.router)
app.include_router(admin.router)
app.include_router(runs.router)
app.include_router(ingestion.router)


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "UP", "service": settings.app_name}


@app.get("/health/ready")
async def ready() -> JSONResponse:
    container = get_container()
    checks: dict[str, bool] = {
        "database": False,
        "redis": False,
        "security": settings.security_configured,
        "model": settings.model_configured,
    }
    try:
        async with container.database.sessions() as session:
            await session.execute(text("SELECT 1"))
            checks["database"] = True
    except Exception:
        pass
    with suppress(RedisError):
        checks["redis"] = bool(await container.redis.ping())
    healthy = all(checks.values())
    return JSONResponse(
        status_code=200 if healthy else 503,
        content={"status": "UP" if healthy else "DOWN", "checks": checks},
    )


@app.get("/metrics", response_class=PlainTextResponse)
async def metrics() -> PlainTextResponse:
    return PlainTextResponse(generate_latest(), media_type=CONTENT_TYPE_LATEST)


FastAPIInstrumentor.instrument_app(app)
