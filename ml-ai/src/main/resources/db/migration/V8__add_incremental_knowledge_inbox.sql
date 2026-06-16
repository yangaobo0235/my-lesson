-- Inbox fields required for incremental knowledge synchronization.
ALTER TABLE ai_inbox_event
    ADD COLUMN IF NOT EXISTS aggregate_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS event_version BIGINT,
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(500),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_ai_inbox_event_status
    ON ai_inbox_event(status, updated_at);
