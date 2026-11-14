"""将空的0001骨架升级到办公IT支持合同，非空历史必须先显式映射。"""

import re
from pathlib import Path

from alembic import context, op
from sqlalchemy import text

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

BASELINE_TABLES = (
    "ai_index_job",
    "ai_chunk",
    "ai_conversation",
    "ai_generation",
    "ai_citation",
    "ai_ticket_draft",
    "ai_feedback",
)


def upgrade() -> None:
    """在线先核验全部旧表为空；离线输出仅供审阅，不能绕过在线入口执行。"""
    if not context.is_offline_mode():
        connection = op.get_bind()
        database = connection.execute(text("SELECT DATABASE()")).scalar_one()
        if not re.fullmatch(r"desk_ai_(test|ci|prod)", database):
            raise RuntimeError("迁移目标库不属于AI服务")
        for table in BASELINE_TABLES:
            if connection.execute(text(f"SELECT 1 FROM `{table}` LIMIT 1")).first():
                raise RuntimeError(f"旧业务表非空，须先完成显式数据映射，未执行结构升级：{table}")
    sql = (Path(__file__).parents[1] / "sql/0002_office_it_support.sql").read_text(encoding="utf-8")
    for statement in re.split(r";\s*(?:\r?\n|$)", sql):
        if statement.strip():
            op.execute(statement.replace(":", r"\:"))


def downgrade() -> None:
    """结构收缩不能安全还原业务语义，仅允许使用已验证备份恢复。"""
    raise RuntimeError("不支持破坏性降级，请使用升级前备份恢复")
