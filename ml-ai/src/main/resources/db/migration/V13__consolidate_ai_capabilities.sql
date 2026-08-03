-- Consolidate course knowledge sources and the versioned confirmation workflow.
UPDATE ai_learning_plan_draft
SET status = 'WAITING_CONFIRMATION',
    state_json = jsonb_set(
        state_json,
        '{status}',
        '"WAITING_CONFIRMATION"'::jsonb),
    updated_at = now()
WHERE status IN ('WAITING_APPROVAL', 'PENDING_CONFIRMATION');

UPDATE ai_learning_plan_draft
SET status = 'CONFIRMED',
    state_json = jsonb_set(
        state_json,
        '{status}',
        '"CONFIRMED"'::jsonb),
    updated_at = now()
WHERE status = 'APPROVED';

DELETE FROM vector_store
WHERE metadata ->> 'source_type' IN ('ARTICLE', 'NOTICE');

DELETE FROM ai_knowledge_source
WHERE source_type IN ('ARTICLE', 'NOTICE');

DROP TABLE IF EXISTS ai_approval_task;

DROP INDEX IF EXISTS idx_ai_tool_call_source_created;
ALTER TABLE ai_tool_call
    DROP COLUMN IF EXISTS tool_source,
    DROP COLUMN IF EXISTS mcp_server_name,
    DROP COLUMN IF EXISTS external_tool_name;
