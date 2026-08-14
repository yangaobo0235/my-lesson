"""Add parent-child retrieval metadata.

Revision ID: 0006
Revises: 0005
Create Date: 2026-08-14
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE knowledge_chunk
            ADD COLUMN parent_content TEXT,
            ADD COLUMN section_path VARCHAR(1000);
        """
    )


def downgrade() -> None:
    op.execute(
        """
        ALTER TABLE knowledge_chunk DROP COLUMN IF EXISTS section_path;
        ALTER TABLE knowledge_chunk DROP COLUMN IF EXISTS parent_content;
        """
    )
