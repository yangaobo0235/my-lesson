package com.yangaobo.ai.tool.repository;

import com.yangaobo.ai.tool.model.ToolCallRecord;
import com.yangaobo.ai.tool.model.ToolCallView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
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
        return startRead(
                runId,
                userId,
                requestId,
                toolName,
                requestJson,
                "LOCAL",
                null,
                null);
    }

    public UUID startRead(
            UUID runId,
            Long userId,
            UUID requestId,
            String toolName,
            String requestJson,
            String toolSource,
            String mcpServerName,
            String externalToolName) {
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
                    request_hash,
                    tool_source,
                    mcp_server_name,
                    external_tool_name,
                    success,
                    write_operation,
                    status,
                    created_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?,
                    false, false, 'STARTED', now()
                )
                """,
                id,
                runId,
                userId,
                requestId,
                toolName,
                requestJson,
                hash(requestJson),
                toolSource,
                mcpServerName,
                externalToolName);
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
                    request_hash,
                    success,
                    write_operation,
                    status,
                    created_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, CAST(? AS jsonb), ?,
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
                requestJson,
                hash(requestJson));
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
                    response_hash = ?,
                    success = true,
                    status = 'SUCCEEDED',
                    latency_ms = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                responseJson,
                hash(responseJson),
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
                    response_hash = ?,
                    success = false,
                    status = ?,
                    error_code = ?,
                    latency_ms = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                responseJson,
                hash(responseJson),
                status,
                errorCode,
                latencyMillis,
                id);
    }

    public List<ToolCallView> recentCalls(
            Long userId,
            String toolName,
            String toolSource,
            String accessType,
            String status,
            int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    id,
                    run_id,
                    user_id,
                    request_id,
                    tool_name,
                    COALESCE(tool_source, 'LOCAL') AS tool_source,
                    mcp_server_name,
                    external_tool_name,
                    write_operation,
                    status,
                    success,
                    latency_ms,
                    error_code,
                    request_hash,
                    response_hash,
                    request_json::text AS request_json,
                    response_json::text AS response_json,
                    created_at,
                    finished_at
                FROM ai_tool_call
                WHERE 1 = 1
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (userId != null) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        if (toolName != null && !toolName.isBlank()) {
            sql.append(" AND tool_name = ?");
            args.add(toolName);
        }
        if ("LOCAL".equalsIgnoreCase(toolSource)
                || "MCP".equalsIgnoreCase(toolSource)) {
            sql.append(" AND tool_source = ?");
            args.add(toolSource.toUpperCase());
        }
        if ("READ".equalsIgnoreCase(accessType)
                || "WRITE".equalsIgnoreCase(accessType)) {
            boolean write = "WRITE".equalsIgnoreCase(accessType);
            sql.append(" AND write_operation = ?");
            args.add(write);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapView, args.toArray());
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

    private ToolCallView mapView(ResultSet resultSet, int rowNum)
            throws SQLException {
        boolean writeOperation = resultSet.getBoolean("write_operation");
        return new ToolCallView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("run_id", UUID.class),
                nullableLong(resultSet, "user_id"),
                resultSet.getObject("request_id", UUID.class),
                resultSet.getString("tool_name"),
                resultSet.getString("tool_source"),
                resultSet.getString("mcp_server_name"),
                resultSet.getString("external_tool_name"),
                writeOperation ? "WRITE" : "READ",
                resultSet.getString("status"),
                resultSet.getBoolean("success"),
                nullableLong(resultSet, "latency_ms"),
                resultSet.getString("error_code"),
                resultSet.getString("request_hash"),
                resultSet.getString("response_hash"),
                resultSet.getString("request_json"),
                resultSet.getString("response_json"),
                instant(resultSet, "created_at"),
                instant(resultSet, "finished_at"));
    }

    private static Long nullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String hash(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 digest is unavailable",
                    exception);
        }
    }
}
