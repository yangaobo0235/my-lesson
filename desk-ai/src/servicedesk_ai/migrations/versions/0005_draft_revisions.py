"""不可变草稿修订及确认摘要；拒绝推断旧草稿采用历史。作者：yangaobo。"""

import re

from alembic import context, op
from sqlalchemy import inspect, text

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """单条原子DDL；既有草稿非空时停止，不编造根或内容确认。"""
    if context.is_offline_mode():
        raise RuntimeError("草稿修订迁移必须在线核对旧数据")
    db = op.get_bind()
    if not re.fullmatch(
        r"desk_ai_(test|ci|prod)", db.execute(text("SELECT DATABASE()")).scalar_one()
    ):
        raise RuntimeError("迁移目标库不属于AI服务")
    inspector = inspect(db)
    columns = {c["name"] for c in inspector.get_columns("ai_action_draft")}
    if "draft_root_id" in columns:
        checks = {c["name"] for c in inspector.get_check_constraints("ai_action_draft")}
        if (
            not {
                "draft_root_id",
                "revision_number",
                "previous_draft_id",
                "content_sha256",
                "status",
            }
            <= columns
            or "ck_ai_draft_revision" not in checks
        ):
            raise RuntimeError("草稿修订结构不完整")
        return
    if db.execute(text("SELECT 1 FROM ai_action_draft LIMIT 1")).first():
        raise RuntimeError("旧草稿非空，须先映射历史确认与采用，未执行DDL")
    op.execute("""
        ALTER TABLE ai_action_draft
        ADD draft_root_id VARCHAR(32) NOT NULL,
        ADD revision_number INT NOT NULL,
        ADD previous_draft_id VARCHAR(32) NULL,
        ADD content_sha256 VARCHAR(64) NOT NULL,
        ADD status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
        ADD UNIQUE KEY uk_ai_draft_revision(draft_root_id,revision_number),
        ADD UNIQUE KEY uk_ai_draft_previous(previous_draft_id),
        ADD CONSTRAINT fk_ai_draft_root FOREIGN KEY(draft_root_id) REFERENCES ai_action_draft(id),
        ADD CONSTRAINT fk_ai_draft_previous FOREIGN KEY(previous_draft_id)
            REFERENCES ai_action_draft(id),
        ADD CONSTRAINT ck_ai_draft_revision CHECK (((revision_number=1 AND draft_root_id=id
            AND previous_draft_id IS NULL) OR (revision_number>1 AND draft_root_id<>id
            AND previous_draft_id IS NOT NULL)) IS TRUE),
        ADD CONSTRAINT ck_ai_draft_hash CHECK (REGEXP_LIKE(content_sha256,'^[0-9a-f]{64}$','c')),
        ADD CONSTRAINT ck_ai_draft_status CHECK (status IN
            ('PENDING','SUPERSEDED','WITHDRAWN','EXPIRED'))
    """)


def downgrade() -> None:
    """修订与确认事实不可丢弃，恢复使用完整备份。"""
    raise RuntimeError("不支持破坏性草稿降级")
