"""草稿确认等待及正式回执同步事件；不新增表或字段。作者：yangaobo。"""

from alembic import op

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """在单条DDL中扩展事件白名单，已保存的任务历史保持不变。"""
    op.execute("""ALTER TABLE ai_task_event
        DROP CHECK ck_ai_task_event_event_type,
        ADD CONSTRAINT ck_ai_task_event_event_type CHECK ((event_type IN (
            'TASK_CREATED','FACT_CHANGED','CHECK_ANSWERED','CONTEXT_SYNCED',
            'RUN_COMPLETED','TASK_CLOSED','BUDGET_CHANGED','CHECK_EXPIRED',
            'CHECK_CHANGED','HYPOTHESIS_CHANGED','DRAFT_WAITING','DRAFT_CONFIRMED'
        )) IS TRUE)
    """)


def downgrade() -> None:
    """正式采用历史不能降级丢弃，恢复使用完整备份。"""
    raise RuntimeError("不支持丢失草稿确认事件的降级")
