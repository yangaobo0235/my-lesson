"""验证AI入口、错误安全及文档开关，不连接业务依赖。"""

from collections.abc import AsyncIterator

import httpx
import pytest
import pytest_asyncio
from fastapi import FastAPI, Query
from starlette.exceptions import HTTPException

from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.main import create_app


@pytest_asyncio.fixture
async def client() -> AsyncIterator[httpx.AsyncClient]:
    """以真实应用边界构造隔离ASGI客户端。"""
    app = create_app(Settings(infrastructure_enabled=False, environment="dev"))
    _register_probes(app)
    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as session:
        yield session


def _register_probes(app: FastAPI) -> None:
    """只在测试应用注册拒绝、异常及输入边界，不进入生产路由。"""

    @app.get("/__test/input")
    async def input_probe(count: int = Query(gt=0)) -> dict[str, int]:
        """提供真实FastAPI输入验证边界。"""
        return {"count": count}

    @app.get("/__test/busy")
    async def busy_probe() -> None:
        """触发已登记公共拒绝。"""
        raise BusinessError(CommonErrorCode.SERVICE_UNAVAILABLE)

    @app.get("/__test/unsafe")
    async def unsafe_probe() -> None:
        """确认未预期异常的内部消息不可公开。"""
        raise RuntimeError("secret-value")

    @app.get("/__test/rate")
    async def rate_probe() -> None:
        """触发带Retry-After的框架拒绝。"""
        raise HTTPException(429, "secret-value", headers={"Retry-After": "3"})


@pytest.mark.asyncio
async def test_health_keeps_native_payload(client: httpx.AsyncClient) -> None:
    """健康只报告进程，不套普通业务Result。"""
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
    assert len(response.headers["X-Trace-Id"]) == 32


@pytest.mark.asyncio
async def test_framework_errors_keep_status_and_null(client: httpx.AsyncClient) -> None:
    """404/405保留状态、Allow、空值及安全追踪标识。"""
    missing = await client.get("/api/v1/not-implemented")
    assert missing.status_code == 404
    assert missing.json()["code"] == 1002
    assert missing.json()["data"] is None
    assert missing.json()["traceId"] == missing.headers["X-Trace-Id"]
    method = await client.post("/health")
    assert method.status_code == 405
    assert method.json()["code"] == 1006
    assert "GET" in method.headers["Allow"]


@pytest.mark.asyncio
async def test_validation_does_not_echo_input(client: httpx.AsyncClient) -> None:
    """422保持真实状态，并隐藏框架错误中的原始输入。"""
    response = await client.get("/__test/input", params={"count": "secret-value"})
    assert response.status_code == 422
    assert response.json()["code"] == 1001
    assert "secret-value" not in response.text


@pytest.mark.asyncio
async def test_errors_distinguish_known_refusal_and_unknown_failure(
    client: httpx.AsyncClient,
) -> None:
    """依赖503与未知500分别编码，不丢弃null或泄漏内部消息。"""
    busy = await client.get("/__test/busy")
    assert busy.status_code == 503
    assert busy.json()["code"] == 1011
    unsafe = await client.get("/__test/unsafe")
    assert unsafe.status_code == 500
    assert unsafe.json()["code"] == 1
    assert unsafe.json()["traceId"] == unsafe.headers["X-Trace-Id"]
    assert "secret-value" not in unsafe.text
    rate = await client.get("/__test/rate")
    assert rate.status_code == 429
    assert rate.json()["code"] == 1010
    assert rate.headers["Retry-After"] == "3"


@pytest.mark.asyncio
async def test_documentation_is_native_in_dev_and_closed_in_prod(client: httpx.AsyncClient) -> None:
    """验证真实配置下的文档路由，不伪造框架OpenAPI版本。"""
    development = await client.get("/openapi.json")
    assert development.status_code == 200
    assert development.json()["openapi"].startswith("3.")
    assert "code" not in development.json()
    transport = httpx.ASGITransport(
        app=create_app(Settings(infrastructure_enabled=False, environment="prod"))
    )
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as production:
        for path in ["/openapi.json", "/docs", "/redoc"]:
            assert (await production.get(path)).status_code == 404
        assert (await production.get("/health")).status_code == 200
