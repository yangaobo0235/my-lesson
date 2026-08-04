"""Create the Python Agent-owned schema.

Revision ID: 0001
Revises:
Create Date: 2026-08-03
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")
    op.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")
    op.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto")
    op.execute(
        """
        CREATE TABLE agent_conversation (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id BIGINT NOT NULL,
            title VARCHAR(200) NOT NULL DEFAULT '新对话',
            status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE INDEX ix_agent_conversation_user_id ON agent_conversation(user_id);

        CREATE TABLE agent_message (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            conversation_id UUID NOT NULL REFERENCES agent_conversation(id),
            role VARCHAR(16) NOT NULL,
            content TEXT NOT NULL,
            citations JSONB NOT NULL DEFAULT '[]'::jsonb,
            request_id UUID,
            trace_id VARCHAR(128),
            token_count INT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE INDEX ix_agent_message_conversation_created
            ON agent_message(conversation_id, created_at);

        CREATE TABLE agent_run (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            conversation_id UUID NOT NULL REFERENCES agent_conversation(id),
            user_id BIGINT NOT NULL,
            request_id UUID NOT NULL,
            user_message_id UUID NOT NULL,
            assistant_message_id UUID,
            status VARCHAR(32) NOT NULL,
            intent VARCHAR(64),
            profile_name VARCHAR(100),
            profile_version VARCHAR(32),
            conservative_mode BOOLEAN NOT NULL DEFAULT false,
            model_name VARCHAR(100),
            tool_call_count INT NOT NULL DEFAULT 0,
            prompt_tokens INT,
            completion_tokens INT,
            latency_ms BIGINT,
            error_message TEXT,
            trace_id VARCHAR(128) NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            finished_at TIMESTAMPTZ
        );
        CREATE UNIQUE INDEX uq_agent_run_conversation_request
            ON agent_run(conversation_id, request_id);
        CREATE INDEX ix_agent_run_user_created ON agent_run(user_id, created_at);

        CREATE TABLE agent_run_event (
            id BIGSERIAL PRIMARY KEY,
            run_id UUID NOT NULL REFERENCES agent_run(id),
            conversation_id UUID NOT NULL,
            sequence INT NOT NULL,
            event_type VARCHAR(64) NOT NULL,
            event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE UNIQUE INDEX uq_agent_run_event_sequence
            ON agent_run_event(run_id, sequence);

        CREATE TABLE agent_tool_call (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            run_id UUID NOT NULL REFERENCES agent_run(id),
            tool_name VARCHAR(100) NOT NULL,
            request_json JSONB NOT NULL DEFAULT '{}'::jsonb,
            response_json JSONB NOT NULL DEFAULT '{}'::jsonb,
            success BOOLEAN NOT NULL,
            latency_ms BIGINT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE TABLE knowledge_source (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            source_type VARCHAR(32) NOT NULL,
            source_id VARCHAR(100) NOT NULL,
            title VARCHAR(500) NOT NULL,
            source_url VARCHAR(1000) NOT NULL,
            content_hash VARCHAR(64) NOT NULL,
            content_version BIGINT NOT NULL DEFAULT 1,
            status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
            error_message TEXT,
            indexed_at TIMESTAMPTZ,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            UNIQUE(source_type, source_id)
        );
        CREATE INDEX ix_knowledge_source_status ON knowledge_source(status, updated_at);

        CREATE TABLE knowledge_chunk (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE CASCADE,
            chunk_index INT NOT NULL,
            title VARCHAR(500) NOT NULL,
            content TEXT NOT NULL,
            metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
            embedding vector(1024) NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            UNIQUE(source_id, chunk_index)
        );
        CREATE INDEX ix_knowledge_chunk_embedding_hnsw
            ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);
        CREATE INDEX ix_knowledge_chunk_content_trgm
            ON knowledge_chunk USING gin (content gin_trgm_ops);

        CREATE TABLE agent_retrieval_trace (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            run_id UUID NOT NULL REFERENCES agent_run(id),
            query TEXT NOT NULL,
            hit_count INT NOT NULL,
            reranked BOOLEAN NOT NULL DEFAULT false,
            candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE INDEX ix_agent_retrieval_trace_run_id ON agent_retrieval_trace(run_id);

        CREATE TABLE evaluation_run (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            mode VARCHAR(32) NOT NULL,
            status VARCHAR(32) NOT NULL,
            summary JSONB NOT NULL DEFAULT '{}'::jsonb,
            report JSONB NOT NULL DEFAULT '{}'::jsonb,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            finished_at TIMESTAMPTZ
        );
        """
    )


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS evaluation_run;
        DROP TABLE IF EXISTS agent_retrieval_trace;
        DROP TABLE IF EXISTS knowledge_chunk;
        DROP TABLE IF EXISTS knowledge_source;
        DROP TABLE IF EXISTS agent_tool_call;
        DROP TABLE IF EXISTS agent_run_event;
        DROP TABLE IF EXISTS agent_run;
        DROP TABLE IF EXISTS agent_message;
        DROP TABLE IF EXISTS agent_conversation;
        """
    )
