-- Bounded learning-plan graph state and versioned user adjustments.
ALTER TABLE ai_learning_plan_draft
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS previous_draft_id UUID REFERENCES ai_learning_plan_draft(id),
    ADD COLUMN IF NOT EXISTS validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS review_result JSONB,
    ADD COLUMN IF NOT EXISTS search_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS repair_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS adjustment_request TEXT;

CREATE INDEX IF NOT EXISTS idx_ai_learning_plan_draft_previous
    ON ai_learning_plan_draft(previous_draft_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_learning_plan_draft_user_version
    ON ai_learning_plan_draft(user_id, id, version);

-- Agent execution metadata required for route and budget diagnostics.
ALTER TABLE ai_agent_run
    ADD COLUMN IF NOT EXISTS agent_profile_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS route_confidence DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS conservative_mode BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS model_call_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tool_call_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS checkpoint_thread_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS termination_reason VARCHAR(100);

CREATE TABLE IF NOT EXISTS ai_retrieval_trace (
    id UUID PRIMARY KEY,
    run_id UUID REFERENCES ai_agent_run(id),
    query_hash VARCHAR(64) NOT NULL,
    rewritten_query_hash VARCHAR(64),
    vector_candidate_count INT NOT NULL,
    keyword_candidate_count INT NOT NULL,
    fused_candidate_count INT NOT NULL,
    rerank_applied BOOLEAN NOT NULL,
    rerank_fallback BOOLEAN NOT NULL,
    final_hit_count INT NOT NULL,
    no_answer_reason VARCHAR(100),
    latency_breakdown JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_retrieval_trace_run
    ON ai_retrieval_trace(run_id, created_at);
