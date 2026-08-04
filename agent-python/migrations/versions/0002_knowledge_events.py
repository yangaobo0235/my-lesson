"""Add reliable knowledge event ingestion.

Revision ID: 0002
Revises: 0001
Create Date: 2026-08-03
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE knowledge_event (
            event_id UUID PRIMARY KEY,
            event_type VARCHAR(64) NOT NULL,
            source_type VARCHAR(32) NOT NULL,
            source_id VARCHAR(100) NOT NULL,
            content_version BIGINT NOT NULL,
            status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
            attempts INT NOT NULL DEFAULT 0,
            last_error TEXT,
            received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            processed_at TIMESTAMPTZ
        );
        CREATE INDEX ix_knowledge_event_status_received
            ON knowledge_event(status, received_at);
        CREATE INDEX ix_knowledge_event_source_version
            ON knowledge_event(source_type, source_id, content_version);
        """
    )


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS knowledge_event")
