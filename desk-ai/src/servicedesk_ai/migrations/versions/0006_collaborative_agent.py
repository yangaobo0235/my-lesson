"""会话/任务/消息/Run持久核心切换；不推断旧诊断数据的任务与事实来源。作者：yangaobo。"""

import re
from pathlib import Path

from alembic import context, op
from sqlalchemy import inspect, text

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None

LEGACY = (
    "ai_feedback",
    "ai_citation",
    "ai_source_usage",
    "ai_action_draft",
    "ai_diagnostic_fact",
    "ai_tool_call",
    "ai_model_call",
    "ai_generation",
    "ai_conversation",
)


def upgrade() -> None:
    """先完整预检再DDL；旧核心非空或DDL中断结构均拒绝启动，不关闭FK校验。"""
    if context.is_offline_mode():
        raise RuntimeError("Run核心迁移必须在线核对旧数据")
    db = op.get_bind()
    if not re.fullmatch(
        r"desk_ai_(test|ci|prod)", db.execute(text("SELECT DATABASE()")).scalar_one()
    ):
        raise RuntimeError("迁移目标库不属于AI服务")
    existing = set(inspect(db).get_table_names())
    if not set(LEGACY) <= existing or {"ai_run", "ai_diagnosis_task", "ai_message"} & existing:
        raise RuntimeError("Run切换结构不完整或DDL已部分执行；停止写入并恢复切换前备份后重试")
    for table in LEGACY:
        if db.execute(text(f"SELECT 1 FROM {table} LIMIT 1")).first():
            raise RuntimeError("旧诊断核心非空，须先明确迁移历史来源；未执行任何DDL")
    # MySQL DDL逐条原子提交，不能用Python事务回滚承诺整次切换原子。
    # 子表按外键依赖顺序删除；索引任务与知识片段原样保留。
    for table in LEGACY:
        op.execute(f"DROP TABLE {table}")
    sql = (Path(__file__).resolve().parents[1] / "sql" / "0006_collaborative_agent.sql").read_text(
        encoding="utf-8"
    )
    for statement in sql.split(";"):
        if statement.strip():
            db.exec_driver_sql(statement)


def downgrade() -> None:
    """任务来源及逐次调用不可丢弃，恢复采用完整备份。"""
    raise RuntimeError("不支持破坏性Run核心降级")
