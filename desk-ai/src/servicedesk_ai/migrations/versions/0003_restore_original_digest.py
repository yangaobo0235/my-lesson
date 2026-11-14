"""补回0002遗漏的原件摘要；不为已有任务编造来源摘要。作者：yangaobo。"""

import re

from alembic import context, op
from sqlalchemy import text

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """非空任务在任何DDL前拒绝，须按可信原件/备份制定独立修复。"""
    if not context.is_offline_mode():
        connection = op.get_bind()
        database = connection.execute(text("SELECT DATABASE()")).scalar_one()
        if not re.fullmatch(r"desk_ai_(test|ci|prod)", database):
            raise RuntimeError("迁移目标库不属于AI服务")
        if connection.execute(text("SELECT 1 FROM ai_index_job LIMIT 1")).first():
            raise RuntimeError("索引任务非空，须先从可信原件或备份核对缺失摘要，未执行DDL")
    op.execute("""
        ALTER TABLE ai_index_job ADD COLUMN file_sha256 VARCHAR(64) NOT NULL
        COMMENT '本次原件字节摘要' AFTER content_format,
        ADD CONSTRAINT ck_ai_index_job__file_sha256_format
        CHECK (REGEXP_LIKE(file_sha256,'^[0-9a-f]{64}$','c'))
    """)


def downgrade() -> None:
    """摘要是原件完整性合同，不提供破坏性删除降级。"""
    raise RuntimeError("不支持删除原件摘要，请使用已验证备份恢复")
