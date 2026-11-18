"""验证启动迁移的就绪边界和生产凭据隔离，不连接共享数据库。"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from io import StringIO
from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config

from servicedesk_ai.bootstrap.migrations import migration_connection
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.main import create_app


def test_offline_migration_preserves_json_schema_literals() -> None:
    """JSON中的冒号必须保留为SQL字面量，不能被SQLAlchemy当成绑定参数。"""
    output = StringIO()
    config = Config(output_buffer=output)
    config.set_main_option(
        "script_location",
        str(Path(__file__).resolve().parents[1] / "src/servicedesk_ai/migrations"),
    )
    command.upgrade(config, "0003", sql=True)
    sql = output.getvalue()
    assert '"maxLength":512' in sql
    assert '"additionalProperties":false' in sql
    assert '"minimum":1' in sql
    assert "\\:" not in sql


def test_private_feedback_migration_requires_online_data_guard() -> None:
    """离线SQL不能绕过旧反馈非空保护。"""
    config = Config(output_buffer=StringIO())
    config.set_main_option(
        "script_location",
        str(Path(__file__).resolve().parents[1] / "src/servicedesk_ai/migrations"),
    )
    with pytest.raises(RuntimeError, match="在线核对"):
        command.upgrade(config, "0003:0004", sql=True)


def connected_settings(*, migrations_enabled: bool = True) -> Settings:
    """提供本用例的组件凭据，真实资源由测试边界替换。"""
    return Settings(
        infrastructure_enabled=True,
        migrations_enabled=migrations_enabled,
        mysql={"password": "test-password"},
        redis={"password": "test-password"},
        elasticsearch={"password": "test-password"},
        qdrant={"api_key": "test-key"},
        model={"api_key": "test-key"},
        rocketmq={"access_key": "test-key", "secret_key": "test-key"},
    )


@pytest.mark.asyncio
@pytest.mark.parametrize("enabled", [True, False])
async def test_startup_migrates_before_opening_business_resources(
    monkeypatch: pytest.MonkeyPatch, enabled: bool
) -> None:
    """正常启动在就绪前完成迁移，关闭开关时不触发结构变更。"""
    events: list[str] = []

    def migrate(settings: Settings) -> None:
        """记录迁移已经完成。"""
        events.append("migrated")

    @asynccontextmanager
    async def resources(settings: Settings) -> AsyncIterator[object]:
        """模拟有明确关闭责任的业务连接。"""
        events.append("opened")
        try:
            yield object()
        finally:
            events.append("closed")

    monkeypatch.setattr("servicedesk_ai.bootstrap.migrations.migrate_database", migrate)
    monkeypatch.setattr("servicedesk_ai.bootstrap.resources.open_resources", resources)

    @asynccontextmanager
    async def domain_rpc(settings):
        """领域连接具有独立于数据库的关闭责任。"""
        events.append("rpc-opened")
        try:
            yield object()
        finally:
            events.append("rpc-closed")

    @asynccontextmanager
    async def rpc_listener(application_services, settings):
        """HTTP就绪前同时建立内部监听，退出顺序与资源依赖相反。"""
        events.append("listener-opened")
        try:
            yield
        finally:
            events.append("listener-closed")

    monkeypatch.setattr("servicedesk_ai.infrastructure.domain_rpc.open_domain_rpc", domain_rpc)
    monkeypatch.setattr("servicedesk_ai.rpc.server.open_rpc_server", rpc_listener)
    monkeypatch.setattr("servicedesk_ai.bootstrap.application.services", lambda *args: object())
    app = create_app(connected_settings(migrations_enabled=enabled))
    async with app.router.lifespan_context(app):
        assert events == (["migrated"] if enabled else []) + [
            "opened",
            "rpc-opened",
            "listener-opened",
        ]
    assert events[-3:] == ["listener-closed", "rpc-closed", "closed"]
    assert events[-1] == "closed"


@pytest.mark.asyncio
async def test_migration_failure_prevents_readiness(monkeypatch: pytest.MonkeyPatch) -> None:
    """结构不满足版本合同即中止启动，不打开业务连接或假报健康。"""

    def migrate(settings: Settings) -> None:
        """模拟原生迁移失败。"""
        raise RuntimeError("migration-failed")

    @asynccontextmanager
    async def resources(settings: Settings) -> AsyncIterator[object]:
        """迁移失败后不得再创建资源。"""
        pytest.fail("migration failure must prevent resource initialization")
        yield object()

    monkeypatch.setattr("servicedesk_ai.bootstrap.migrations.migrate_database", migrate)
    monkeypatch.setattr("servicedesk_ai.bootstrap.resources.open_resources", resources)
    app = create_app(connected_settings())
    with pytest.raises(RuntimeError, match="migration-failed"):
        async with app.router.lifespan_context(app):
            pytest.fail("startup must not succeed")


def test_migration_rejects_other_database_and_production_runtime_credentials() -> None:
    """独立维护入口不依赖其他组件密钥，也不能绕过数据库归属和生产DDL账号。"""
    with pytest.raises(ValueError, match="AI所属"):
        with migration_connection(
            Settings(infrastructure_enabled=False, mysql={"database": "desk_user_test"})
        ):
            pytest.fail("must fail before connecting")
    with pytest.raises(ValueError, match="独立"):
        with migration_connection(
            Settings(
                infrastructure_enabled=False,
                environment="prod",
                resource_env="prod",
                mysql={"database": "desk_ai_prod"},
            )
        ):
            pytest.fail("must not use production runtime credentials for DDL")
