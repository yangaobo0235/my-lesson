"""Remove the obsolete PostgreSQL keyword-search objects.

Revision ID: 0005
Revises: 0004
Create Date: 2026-08-05
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0005"
down_revision: str | None = "0004"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute("DROP INDEX IF EXISTS ix_knowledge_chunk_content_trgm")
    op.execute("DROP EXTENSION IF EXISTS pg_trgm")


def downgrade() -> None:
    # The removed backend is intentionally not restored.
    pass
