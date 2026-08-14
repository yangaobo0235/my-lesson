"""Classify redacted tool audit records.

Revision ID: 0007
Revises: 0006
Create Date: 2026-08-14
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0007"
down_revision: str | None = "0006"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE agent_tool_call
            ADD COLUMN sensitivity_level VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
            ADD COLUMN redaction_version VARCHAR(32) NOT NULL DEFAULT 'audit-v1';
        """
    )


def downgrade() -> None:
    op.execute(
        """
        ALTER TABLE agent_tool_call DROP COLUMN IF EXISTS redaction_version;
        ALTER TABLE agent_tool_call DROP COLUMN IF EXISTS sensitivity_level;
        """
    )
