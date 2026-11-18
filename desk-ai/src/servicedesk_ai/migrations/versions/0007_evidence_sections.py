"""知识父子清单迁移，保留已有片段身份及全部历史来源。作者：yangaobo。"""

from alembic import context, op
from sqlalchemy import text

from servicedesk_ai.diagnosis.budget import estimate_text_tokens

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """旧叶片只补摘要与估算，不重分片、不改变既有索引；新任务生成父章节。"""
    if context.is_offline_mode():
        raise RuntimeError("父子证据迁移必须在线核对正文")
    db = op.get_bind()
    if db.execute(
        text("SELECT id FROM ai_chunk WHERE OCTET_LENGTH(content)>49152 LIMIT 1")
    ).first():
        raise RuntimeError("已有片段超出证据正文预算，迁移前需人工核对")
    op.execute("""ALTER TABLE ai_chunk
        ADD parent_chunk_id VARCHAR(32) NULL COMMENT '同任务父章节',
        ADD node_kind VARCHAR(16) NULL COMMENT 'SECTION或LEAF，仅叶片参与召回',
        ADD content_sha256 VARCHAR(64) NULL COMMENT '固定正文SHA256',
        ADD token_estimate INTEGER NULL COMMENT '固定估算器的正数Token估算'
    """)
    cursor = ""
    while True:
        rows = db.execute(
            text("SELECT id,content FROM ai_chunk WHERE id>:cursor ORDER BY id LIMIT 200"),
            {"cursor": cursor},
        ).all()
        if not rows:
            break
        for identifier, content in rows:
            db.execute(
                text("""UPDATE ai_chunk SET node_kind='LEAF',
                content_sha256=SHA2(content,256),token_estimate=:tokens WHERE id=:id"""),
                {"id": identifier, "tokens": estimate_text_tokens(content)},
            )
        cursor = rows[-1][0]
    op.execute("""ALTER TABLE ai_chunk
        MODIFY node_kind VARCHAR(16) NOT NULL COMMENT 'SECTION或LEAF，仅叶片参与召回',
        MODIFY content_sha256 VARCHAR(64) NOT NULL COMMENT '固定正文SHA256',
        MODIFY token_estimate INTEGER NOT NULL COMMENT '固定估算器的正数Token估算',
        ADD UNIQUE KEY uk_ai_chunk_identity_job(id,index_job_id),
        ADD CONSTRAINT fk_ai_chunk_parent FOREIGN KEY(parent_chunk_id,index_job_id)
            REFERENCES ai_chunk(id,index_job_id),
        ADD CONSTRAINT ck_ai_chunk_parent CHECK (parent_chunk_id IS NULL OR
            (REGEXP_LIKE(parent_chunk_id,'^[0-9a-f]{32}$','c') AND parent_chunk_id<>id)),
        ADD CONSTRAINT ck_ai_chunk_kind CHECK (node_kind IN ('SECTION','LEAF')),
        ADD CONSTRAINT ck_ai_chunk_sha CHECK (REGEXP_LIKE(content_sha256,'^[0-9a-f]{64}$','c')),
        ADD CONSTRAINT ck_ai_chunk_tokens CHECK (token_estimate>0),
        ADD CONSTRAINT ck_ai_chunk_bytes CHECK (OCTET_LENGTH(content)<=49152)
    """)


def downgrade() -> None:
    """父章节已有来源引用，不能静默删除，恢复采用完整备份。"""
    raise RuntimeError("不支持丢失父子证据关系的降级")
