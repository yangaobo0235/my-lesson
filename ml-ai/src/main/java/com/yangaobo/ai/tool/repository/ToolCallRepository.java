package com.yangaobo.ai.tool.repository;

import com.yangaobo.ai.tool.model.ToolCallRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ToolCallRepository {

    private static final String SELECT_COLUMNS = """
            id,
            status,
            success,
            response_json::text AS response_json,
            error_code
            """;

    private final JdbcTemplate jdbcTemplate;

    public ToolCallRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID startRead(
            UUID runId,
            Long userId,
            UUID requestId,
            String toolName,
            String requestJson) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO ai_tool_call (
                    id,
                    run_id,
                    user_id,
                    request_id,
                    tool_name,
                    request_json,
                    success,
                    write_operation,
                    status,
                    created_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, CAST(? AS jsonb),
                    false, false, 'STARTED', now()
                )
                """,
                id,
                runId,
                userId,
                requestId,
                toolName,
                requestJson);
        return id;
    }

    public Optional<UUID> reserveWrite(
            UUID runId,
            Long userId,
            UUID requestId,
            String toolName,
            String requestJson) {
        UUID id = UUID.randomUUID();
        List<UUID> ids = jdbcTemplate.query(
                """
                INSERT INTO ai_tool_call (
                    id,
                    run_id,
                    user_id,
                    request_id,
                    tool_name,
                    request_json,
                    success,
                    write_operation,
                    status,
                    created_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, CAST(? AS jsonb),
                    false, true, 'STARTED', now()
                )
                ON CONFLICT (request_id, tool_name)
                    WHERE write_operation = true
                      AND request_id IS NOT NULL
                DO NOTHING
                RETURNING id
                """,
                (resultSet, rowNum) ->
                        resultSet.getObject("id", UUID.class),
                id,
                runId,
                userId,
                requestId,
                toolName,
                requestJson);
        return ids.stream().findFirst();
    }

    public Optional<ToolCallRecord> findWrite(
            UUID requestId,
            String toolName) {
        List<ToolCallRecord> calls = jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_tool_call
                WHERE request_id = ?
                  AND tool_name = ?
                  AND write_operation = true
                LIMIT 1
                """,
                this::map,
                requestId,
                toolName);
        return calls.stream().findFirst();
    }

    public void complete(
            UUID id,
            String responseJson,
            long latencyMillis) {
        jdbcTemplate.update(
                """
                UPDATE ai_tool_call
                SET response_json = CAST(? AS jsonb),
                    success = true,
                    status = 'SUCCEEDED',
                    latency_ms = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                responseJson,
                latencyMillis,
                id);
    }

    public void fail(
            UUID id,
            String status,
            String errorCode,
            String responseJson,
            long latencyMillis) {
        jdbcTemplate.update(
                """
                UPDATE ai_tool_call
                SET response_json = CAST(? AS jsonb),
                    success = false,
                    status = ?,
                    error_code = ?,
                    latency_ms = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                responseJson,
                status,
                errorCode,
                latencyMillis,
                id);
    }

    private ToolCallRecord map(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new ToolCallRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("status"),
                resultSet.getBoolean("success"),
                resultSet.getString("response_json"),
                resultSet.getString("error_code"));
    }
}
