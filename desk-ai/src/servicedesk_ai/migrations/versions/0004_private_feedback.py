"""将空的旧共享反馈结构收敛为本人评价，不删除已有业务评价。作者：yangaobo。"""

import re

from alembic import context, op
from sqlalchemy import inspect, text

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """旧评价没有采纳状态；非空时拒绝DDL，不编造采纳或丢弃人工处置。"""
    if context.is_offline_mode():
        raise RuntimeError("反馈迁移必须在线核对旧数据，禁止绕过非空保护")
    connection = op.get_bind()
    database = connection.execute(text("SELECT DATABASE()")).scalar_one()
    if not re.fullmatch(r"desk_ai_(test|ci|prod)", database):
        raise RuntimeError("迁移目标库不属于AI服务")
    inspector = inspect(connection)
    columns = {column["name"] for column in inspector.get_columns("ai_feedback")}
    expected = {
        "id",
        "run_id",
        "submitted_by",
        "rating",
        "adoption",
        "issue_category",
        "comment",
        "created_at",
        "updated_at",
        "row_version",
    }
    if "run_id" in columns:
        # 单条DDL已成功但Alembic版本提交前中断时，只接受完整的新结构。
        constraints = {item["name"] for item in inspector.get_check_constraints("ai_feedback")}
        if (
            columns != expected
            or not {"ck_ai_feedback_adoption", "ck_ai_feedback_issue"} <= constraints
        ):
            raise RuntimeError("反馈结构不完整，拒绝推测迁移成功")
        return
    if columns != (expected - {"run_id", "adoption"}) | {
        "generation_id",
        "share_with_knowledge_admin",
        "status",
        "handled_by",
        "handling_note",
        "handled_at",
    }:
        raise RuntimeError("旧反馈结构与已登记迁移不匹配")
    if connection.execute(text("SELECT 1 FROM ai_feedback LIMIT 1")).first():
        raise RuntimeError("旧反馈非空且缺少采纳事实，需明确映射和保留人工处置记录，未执行DDL")
    clauses = []
    for item in inspector.get_check_constraints("ai_feedback"):
        name = item["name"]
        if name is None or not re.fullmatch(r"ck_ai_feedback__[a-z0-9_]+", name):
            raise RuntimeError("存在未登记的反馈约束，拒绝删除")
        clauses.append(f"DROP CHECK `{name}`")
    clauses.extend(
        [
            "DROP FOREIGN KEY fk_ai_feedback__generation_owner",
            "DROP INDEX uk_ai_feedback_generation",
            "DROP INDEX idx_ai_feedback_pending_shared",
            "DROP INDEX idx_ai_feedback_generation_owner",
            "CHANGE COLUMN generation_id run_id VARCHAR(32) NOT NULL COMMENT '原运行标识'",
            "ADD COLUMN adoption VARCHAR(16) NOT NULL COMMENT '本人实际采纳情况' AFTER rating",
            "MODIFY COLUMN comment VARCHAR(2000) NULL COMMENT '本人反馈，不自动共享'",
            "DROP COLUMN share_with_knowledge_admin",
            "DROP COLUMN status",
            "DROP COLUMN handled_by",
            "DROP COLUMN handling_note",
            "DROP COLUMN handled_at",
            "ADD UNIQUE KEY uk_ai_feedback_run_owner (run_id,submitted_by)",
            "ADD KEY idx_ai_feedback_owner_created (submitted_by,created_at,id)",
            "ADD CONSTRAINT fk_ai_feedback_run_owner FOREIGN KEY (run_id,submitted_by) "
            "REFERENCES ai_generation(id,owner_id)",
            "ADD CONSTRAINT ck_ai_feedback_id CHECK (REGEXP_LIKE(id,'^[0-9a-f]{32}$','c'))",
            "ADD CONSTRAINT ck_ai_feedback_run CHECK (REGEXP_LIKE(run_id,'^[0-9a-f]{32}$','c'))",
            "ADD CONSTRAINT ck_ai_feedback_owner "
            "CHECK (REGEXP_LIKE(submitted_by,'^[0-9a-f]{32}$','c'))",
            "ADD CONSTRAINT ck_ai_feedback_rating CHECK (rating IN ('HELPFUL','UNHELPFUL'))",
            "ADD CONSTRAINT ck_ai_feedback_adoption "
            "CHECK (adoption IN ('NOT_USED','ACCEPTED','MODIFIED','REJECTED'))",
            """ADD CONSTRAINT ck_ai_feedback_issue CHECK (
            (rating='HELPFUL' AND issue_category IS NULL)
            OR (rating='UNHELPFUL' AND issue_category IS NOT NULL
              AND issue_category IN (
                'INCORRECT','IRRELEVANT','MISSING_CONTEXT','UNSUPPORTED','OTHER'))
            )""",
            "ADD CONSTRAINT ck_ai_feedback_comment "
            "CHECK (comment IS NULL OR REGEXP_LIKE(comment,'[^[:space:]]'))",
            "ADD CONSTRAINT ck_ai_feedback_version CHECK (row_version>=0)",
            "ADD CONSTRAINT ck_ai_feedback_time CHECK (updated_at>=created_at)",
        ]
    )
    # MySQL8的单条InnoDB DDL原子提交；不留下DROP成功但CREATE未成功的窗口。
    op.execute("ALTER TABLE ai_feedback " + ",\n".join(clauses))


def downgrade() -> None:
    """反馈不降级为重新开放聊天的共享处置流程。"""
    raise RuntimeError("不支持破坏性反馈降级，请使用经验证备份恢复")
