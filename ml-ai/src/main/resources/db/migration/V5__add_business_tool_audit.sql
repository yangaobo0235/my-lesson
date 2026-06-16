-- Auditing and idempotency fields for business tool calls.
ALTER TABLE ai_tool_call
    ADD COLUMN user_id BIGINT,
    ADD COLUMN request_id UUID,
    ADD COLUMN write_operation BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED',
    ADD COLUMN error_code VARCHAR(64),
    ADD COLUMN finished_at TIMESTAMPTZ;

UPDATE ai_tool_call
SET status = CASE
    WHEN success THEN 'SUCCEEDED'
    ELSE 'FAILED'
END,
finished_at = created_at;

CREATE INDEX idx_ai_tool_call_run_created
    ON ai_tool_call(run_id, created_at);

CREATE INDEX idx_ai_tool_call_user_created
    ON ai_tool_call(user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ai_tool_call_write_request
    ON ai_tool_call(request_id, tool_name)
    WHERE write_operation = true
      AND request_id IS NOT NULL;

COMMENT ON COLUMN ai_tool_call.user_id IS '执行工具时从 UserContext 取得的用户 ID';
COMMENT ON COLUMN ai_tool_call.request_id IS '所属对话请求 UUID，写工具幂等键的一部分';
COMMENT ON COLUMN ai_tool_call.write_operation IS '是否为会改变业务数据的写工具';
COMMENT ON COLUMN ai_tool_call.status IS '工具状态：STARTED、SUCCEEDED、FAILED、TIMED_OUT';
COMMENT ON COLUMN ai_tool_call.error_code IS '脱敏后的可恢复错误码';
COMMENT ON COLUMN ai_tool_call.finished_at IS '工具调用完成时间';
