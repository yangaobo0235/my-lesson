ALTER TABLE ai_tool_call
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS response_hash VARCHAR(64);

COMMENT ON COLUMN ai_tool_call.request_hash
    IS '脱敏后工具入参 JSON 的 SHA-256 摘要';
COMMENT ON COLUMN ai_tool_call.response_hash
    IS '脱敏后工具出参 JSON 的 SHA-256 摘要';
