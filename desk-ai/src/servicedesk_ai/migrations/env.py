"""为应用启动和Alembic命令提供相同的连接及版本执行环境。"""

from alembic import context
from sqlalchemy import Connection

from servicedesk_ai.bootstrap.migrations import migration_connection
from servicedesk_ai.bootstrap.settings import load_settings


def run_migrations(connection: Connection) -> None:
    """使用调用方已取得锁的连接，版本记录与迁移由Alembic维护。"""
    context.configure(connection=connection, target_metadata=None)
    with context.begin_transaction():
        context.run_migrations()


if context.is_offline_mode():
    context.configure(dialect_name="mysql", literal_binds=True, target_metadata=None)
    with context.begin_transaction():
        context.run_migrations()
else:
    provided = context.config.attributes.get("connection")
    if provided is not None:
        run_migrations(provided)
    else:
        with migration_connection(load_settings(validate_infrastructure=False)) as connection:
            run_migrations(connection)
