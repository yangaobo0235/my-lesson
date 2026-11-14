"""在API就绪前执行所属库迁移；命令行维护复用同一凭据和数据库锁。"""

from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import URL, Connection, create_engine, inspect, text

from servicedesk_ai.bootstrap.settings import Settings


@contextmanager
def migration_connection(settings: Settings) -> Iterator[Connection]:
    """验证所属数据库并独占迁移，MySQL会话退出时也会释放命名锁。"""
    mysql = settings.mysql
    expected = "prod" if settings.environment == "prod" else settings.resource_env
    if expected != settings.resource_env or (settings.environment == "dev" and expected == "prod"):
        raise ValueError("迁移资源环境与基础环境不一致")
    if mysql.database != f"desk_ai_{expected}":
        raise ValueError("迁移仅允许访问AI所属环境数据库")
    if settings.environment == "prod" and (
        not mysql.migration_username.strip() or not mysql.migration_password.get_secret_value()
    ):
        raise ValueError("生产迁移必须配置独立migration_username和migration_password")
    username = mysql.migration_username or mysql.username
    password = mysql.migration_password.get_secret_value() or mysql.password.get_secret_value()
    if not username.strip() or not password:
        raise ValueError("数据库迁移账号和密码不能为空")
    engine = create_engine(
        URL.create(
            "mysql+pymysql",
            username=username,
            password=password,
            host=mysql.host,
            port=mysql.port,
            database=mysql.database,
        ),
        hide_parameters=True,
        connect_args={
            "connect_timeout": 3,
            "read_timeout": 60,
            "write_timeout": 60,
            "charset": "utf8mb4",
            "init_command": "SET time_zone = '+00:00', innodb_lock_wait_timeout = 5",
        },
    )
    try:
        with engine.connect() as connection:
            lock_name = f"desk-ai-migration:{mysql.database}"
            acquired = connection.scalar(text("SELECT GET_LOCK(:name, 30)"), {"name": lock_name})
            connection.commit()
            if acquired != 1:
                raise RuntimeError("等待AI数据库迁移锁超时，拒绝带旧结构启动")
            try:
                yield connection
                connection.commit()
            finally:
                connection.rollback()
                connection.execute(text("SELECT RELEASE_LOCK(:name)"), {"name": lock_name})
                connection.commit()
    finally:
        engine.dispose()


def migration_config(connection: Connection) -> Config:
    """使用随源码及wheel分发的唯一迁移链，不依赖运行工作目录。"""
    config = Config()
    config.set_main_option(
        "script_location", str(Path(__file__).resolve().parents[1] / "migrations")
    )
    config.attributes["connection"] = connection
    return config


def migrate_database(settings: Settings) -> None:
    """仅执行未应用的revision；已有表无历史时拒绝隐式stamp或覆盖。"""
    with migration_connection(settings) as connection:
        tables = inspect(connection).get_table_names()
        connection.commit()
        if tables and "alembic_version" not in tables:
            raise RuntimeError("AI库已有表但无迁移历史；核对结构后显式执行alembic stamp 0001")
        command.upgrade(migration_config(connection), "head")
