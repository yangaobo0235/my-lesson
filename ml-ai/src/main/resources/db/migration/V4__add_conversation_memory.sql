-- Persistent conversation memory and idempotent run tracking.
ALTER TABLE ai_message
    ADD COLUMN request_id UUID,
    ADD COLUMN citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN summary_until TIMESTAMPTZ,
    ADD COLUMN summary_until_message_id UUID;

ALTER TABLE ai_agent_run
    ADD COLUMN request_id UUID,
    ADD COLUMN user_message_id UUID,
    ADD COLUMN assistant_message_id UUID;

ALTER TABLE ai_agent_run
    ADD CONSTRAINT fk_ai_agent_run_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id),
    ADD CONSTRAINT fk_ai_agent_run_user_message
        FOREIGN KEY (user_message_id) REFERENCES ai_message(id),
    ADD CONSTRAINT fk_ai_agent_run_assistant_message
        FOREIGN KEY (assistant_message_id) REFERENCES ai_message(id);

CREATE UNIQUE INDEX uq_ai_agent_run_conversation_request
    ON ai_agent_run(conversation_id, request_id)
    WHERE request_id IS NOT NULL;

CREATE INDEX idx_ai_conversation_user_updated
    ON ai_conversation(user_id, updated_at DESC)
    WHERE status <> 'DELETED';

CREATE INDEX idx_ai_message_dialog
    ON ai_message(conversation_id, created_at, id)
    WHERE role IN ('USER', 'ASSISTANT');

CREATE INDEX idx_ai_message_summary
    ON ai_message(conversation_id, created_at DESC)
    WHERE role = 'SYSTEM_SUMMARY';

COMMENT ON COLUMN ai_message.request_id IS '前端请求 UUID，用于关联同一轮用户和助手消息';
COMMENT ON COLUMN ai_message.citations IS '助手消息使用的结构化引用';
COMMENT ON COLUMN ai_message.summary_until IS 'SYSTEM_SUMMARY 已覆盖到的消息时间';
COMMENT ON COLUMN ai_message.summary_until_message_id IS 'SYSTEM_SUMMARY 已覆盖到的最后消息 ID';
COMMENT ON COLUMN ai_agent_run.request_id IS '前端请求 UUID，同一会话内唯一';
COMMENT ON COLUMN ai_agent_run.user_message_id IS '本次运行对应的用户消息';
COMMENT ON COLUMN ai_agent_run.assistant_message_id IS '本次运行生成的助手消息';
