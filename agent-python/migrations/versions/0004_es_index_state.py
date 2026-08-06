"""Track Elasticsearch knowledge index state.

Revision ID: 0004
Revises: 0003
Create Date: 2026-08-05
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0004"
down_revision: str | None = "0003"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE knowledge_source
            ADD COLUMN es_indexed_version BIGINT NOT NULL DEFAULT 0,
            ADD COLUMN es_indexed_at TIMESTAMPTZ,
            ADD COLUMN es_index_error TEXT;
        """
    )


def downgrade() -> None:
    op.execute(
        """
        ALTER TABLE knowledge_source DROP COLUMN IF EXISTS es_index_error;
        ALTER TABLE knowledge_source DROP COLUMN IF EXISTS es_indexed_at;
        ALTER TABLE knowledge_source DROP COLUMN IF EXISTS es_indexed_version;
        """
    )
