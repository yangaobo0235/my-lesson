"""Add durable run queue identity snapshot and claim index.

Revision ID: 0003
Revises: 0002
Create Date: 2026-08-03
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE agent_run
            ADD COLUMN username VARCHAR(200) NOT NULL DEFAULT 'unknown',
            ADD COLUMN user_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
            ADD COLUMN worker_id VARCHAR(100),
            ADD COLUMN heartbeat_at TIMESTAMPTZ,
            ADD COLUMN attempts INT NOT NULL DEFAULT 0;
        CREATE INDEX ix_agent_run_status_created ON agent_run(status, created_at);
        """
    )


def downgrade() -> None:
    op.execute(
        """
        DROP INDEX IF EXISTS ix_agent_run_status_created;
        ALTER TABLE agent_run DROP COLUMN IF EXISTS user_roles;
        ALTER TABLE agent_run DROP COLUMN IF EXISTS username;
        ALTER TABLE agent_run DROP COLUMN IF EXISTS worker_id;
        ALTER TABLE agent_run DROP COLUMN IF EXISTS heartbeat_at;
        ALTER TABLE agent_run DROP COLUMN IF EXISTS attempts;
        """
    )
