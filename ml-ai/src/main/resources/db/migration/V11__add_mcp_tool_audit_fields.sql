ALTER TABLE ai_tool_call
    ADD COLUMN IF NOT EXISTS tool_source VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS mcp_server_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS external_tool_name VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_ai_tool_call_source_created
    ON ai_tool_call(tool_source, created_at DESC);

COMMENT ON COLUMN ai_tool_call.tool_source
    IS '工具来源：LOCAL 表示项目本地业务工具，MCP 表示 Model Context Protocol 外部工具';
COMMENT ON COLUMN ai_tool_call.mcp_server_name
    IS 'MCP 工具所属服务名称';
COMMENT ON COLUMN ai_tool_call.external_tool_name
    IS 'MCP Server 暴露的原始工具名称';
