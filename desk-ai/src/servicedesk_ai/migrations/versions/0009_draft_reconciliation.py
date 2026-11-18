"""草稿撤回和等待释放事件，保留原采用历史及固定表数。作者：yangaobo。"""

from alembic import op

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """原事件不变，仅为维护收敛增加可审计事件类型。"""
    op.execute("""ALTER TABLE ai_task_event
        DROP CHECK ck_ai_task_event_event_type,
        ADD CONSTRAINT ck_ai_task_event_event_type CHECK ((event_type IN (
            'TASK_CREATED','FACT_CHANGED','CHECK_ANSWERED','CONTEXT_SYNCED',
            'RUN_COMPLETED','TASK_CLOSED','BUDGET_CHANGED','CHECK_EXPIRED',
            'CHECK_CHANGED','HYPOTHESIS_CHANGED','DRAFT_WAITING','DRAFT_CONFIRMED',
            'DRAFT_WITHDRAWN','DRAFT_RELEASED'
        )) IS TRUE)
    """)


def downgrade() -> None:
    """正式历史不能丢弃，恢复须使用完整备份。"""
    raise RuntimeError("不支持丢失草稿对账历史的降级")
