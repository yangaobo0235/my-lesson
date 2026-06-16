package com.yangaobo.ai.approval.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.approval.model.ApprovalTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApprovalTaskRepository {

    private static final String SELECT_COLUMNS = """
            id,
            run_id,
            user_id,
            request_id,
            action_type,
            action_payload::text AS action_payload,
            reason,
            status,
            expires_at,
            decided_at,
            created_at,
            updated_at,
            response_json::text AS response_json,
            error_code
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ApprovalTaskRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ApprovalTask createOrFind(
            UUID runId,
            Long userId,
            UUID requestId,
            String actionType,
            JsonNode payload,
            String reason,
            Instant expiresAt) {
        UUID id = UUID.randomUUID();
        List<ApprovalTask> inserted = jdbcTemplate.query(
                """
                INSERT INTO ai_approval_task (
                    id,
                    run_id,
                    user_id,
                    request_id,
                    action_type,
                    action_payload,
                    reason,
                    status,
                    expires_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, CAST(? AS jsonb), ?,
                    'PENDING', ?, now(), now()
                )
                ON CONFLICT (run_id, action_type)
                DO NOTHING
                RETURNING
                """ + SELECT_COLUMNS,
                this::map,
                id,
                runId,
                userId,
                requestId,
                actionType,
                writeJson(payload),
                reason,
                OffsetDateTime.ofInstant(
                        expiresAt,
                        java.time.ZoneOffset.UTC));
        if (!inserted.isEmpty()) {
            return inserted.get(0);
        }
        return findByRunAndAction(runId, actionType)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to load idempotent approval task"));
    }

    public List<ApprovalTask> findByUser(Long userId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_approval_task
                WHERE user_id = ?
                ORDER BY
                    CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END,
                    created_at DESC
                LIMIT ?
                """,
                this::map,
                userId,
                limit);
    }

    public Optional<ApprovalTask> findOwned(UUID id, Long userId) {
        return find(
                """
                WHERE id = ?
                  AND user_id = ?
                """,
                id,
                userId);
    }

    public Optional<ApprovalTask> findByRunAndAction(
            UUID runId,
            String actionType) {
        return find(
                """
                WHERE run_id = ?
                  AND action_type = ?
                """,
                runId,
                actionType);
    }

    public boolean claim(UUID id, Long userId) {
        return jdbcTemplate.update(
                """
                UPDATE ai_approval_task
                SET status = 'EXECUTING',
                    updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'PENDING'
                  AND expires_at > now()
                """,
                id,
                userId) == 1;
    }

    public void expire(UUID id, Long userId) {
        jdbcTemplate.update(
                """
                UPDATE ai_approval_task
                SET status = 'EXPIRED',
                    decided_at = now(),
                    updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'PENDING'
                  AND expires_at <= now()
                """,
                id,
                userId);
    }

    public boolean reject(UUID id, Long userId) {
        return jdbcTemplate.update(
                """
                UPDATE ai_approval_task
                SET status = 'REJECTED',
                    decided_at = now(),
                    updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'PENDING'
                  AND expires_at > now()
                """,
                id,
                userId) == 1;
    }

    public void approve(UUID id, JsonNode response) {
        jdbcTemplate.update(
                """
                UPDATE ai_approval_task
                SET status = 'APPROVED',
                    response_json = CAST(? AS jsonb),
                    decided_at = now(),
                    updated_at = now()
                WHERE id = ?
                  AND status = 'EXECUTING'
                """,
                writeJson(response),
                id);
    }

    public void fail(UUID id, String errorCode, JsonNode response) {
        jdbcTemplate.update(
                """
                UPDATE ai_approval_task
                SET status = 'FAILED',
                    error_code = ?,
                    response_json = CAST(? AS jsonb),
                    decided_at = now(),
                    updated_at = now()
                WHERE id = ?
                  AND status = 'EXECUTING'
                """,
                errorCode,
                writeJson(response),
                id);
    }

    private Optional<ApprovalTask> find(
            String where,
            Object... arguments) {
        List<ApprovalTask> tasks = jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_approval_task
                """ + where + """
                LIMIT 1
                """,
                this::map,
                arguments);
        return tasks.stream().findFirst();
    }

    private ApprovalTask map(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new ApprovalTask(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("run_id", UUID.class),
                resultSet.getObject("user_id", Long.class),
                resultSet.getObject("request_id", UUID.class),
                resultSet.getString("action_type"),
                readJson(resultSet.getString("action_payload")),
                resultSet.getString("reason"),
                resultSet.getString("status"),
                instant(resultSet, "expires_at"),
                instant(resultSet, "decided_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                readJson(resultSet.getString("response_json")),
                resultSet.getString("error_code"));
    }

    private JsonNode readJson(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to read approval JSON", exception);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to write approval JSON",
                    exception);
        }
    }

    private Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        OffsetDateTime value = resultSet.getObject(
                column,
                OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
