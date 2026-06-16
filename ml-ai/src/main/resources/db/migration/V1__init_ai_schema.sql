-- Flyway baseline for the ml-ai PostgreSQL schema.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE ai_conversation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_message (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id),
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    token_count INT,
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_message_conversation
    ON ai_message(conversation_id, created_at);

CREATE TABLE ai_agent_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID,
    user_id BIGINT NOT NULL,
    agent_name VARCHAR(100) NOT NULL,
    intent VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    model_name VARCHAR(100),
    prompt_tokens INT,
    completion_tokens INT,
    latency_ms BIGINT,
    error_message TEXT,
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE TABLE ai_tool_call (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID NOT NULL REFERENCES ai_agent_run(id),
    tool_name VARCHAR(100) NOT NULL,
    request_json JSONB,
    response_json JSONB,
    success BOOLEAN NOT NULL,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_knowledge_source (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_url VARCHAR(1000),
    content_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    indexed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(source_type, source_id)
);

CREATE TABLE vector_store (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content TEXT,
    metadata JSON,
    embedding vector(1024)
);

CREATE INDEX vector_store_embedding_hnsw
    ON vector_store USING HNSW (embedding vector_cosine_ops);

CREATE TABLE ai_approval_task (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID NOT NULL REFERENCES ai_agent_run(id),
    user_id BIGINT NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    action_payload JSONB NOT NULL,
    reason TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_inbox_event (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

CREATE TABLE ai_eval_case (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_type VARCHAR(32) NOT NULL,
    question TEXT NOT NULL,
    expected_json JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_eval_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id UUID NOT NULL REFERENCES ai_eval_case(id),
    model_name VARCHAR(100) NOT NULL,
    answer TEXT,
    metrics JSONB NOT NULL,
    passed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_learning_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id BIGINT NOT NULL,
    goal TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    plan_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
