package com.yangaobo.ai.workflow.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.workflow.model.LearningPlanDraftRecord;
import com.yangaobo.ai.workflow.model.LearningPlanState;
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
public class LearningPlanDraftRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LearningPlanDraftRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public LearningPlanDraftRecord save(
            UUID runId,
            LearningPlanState state) {
        UUID id = UUID.randomUUID();
        List<LearningPlanDraftRecord> inserted = jdbcTemplate.query(
                """
                INSERT INTO ai_learning_plan_draft (
                    id,
                    run_id,
                    user_id,
                    goal,
                    minutes_per_day,
                    state_json,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, now(), now()
                )
                ON CONFLICT (run_id)
                DO NOTHING
                RETURNING
                    id,
                    run_id,
                    state_json::text AS state_json,
                    created_at,
                    updated_at
                """,
                this::map,
                id,
                runId,
                state.userId(),
                state.goal(),
                state.minutesPerDay(),
                writeJson(state),
                state.status());
        if (!inserted.isEmpty()) {
            return inserted.get(0);
        }
        return findByRun(runId)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to load idempotent learning plan draft"));
    }

    public Optional<LearningPlanDraftRecord> findOwned(
            UUID id,
            Long userId) {
        return find(
                """
                WHERE id = ?
                  AND user_id = ?
                """,
                id,
                userId);
    }

    public Optional<LearningPlanDraftRecord> findByRun(UUID runId) {
        return find("WHERE run_id = ?", runId);
    }

    public boolean updateStatus(
            UUID id,
            Long userId,
            String expectedStatus,
            String newStatus) {
        return jdbcTemplate.update(
                """
                UPDATE ai_learning_plan_draft
                SET status = ?,
                    state_json = jsonb_set(
                        state_json,
                        '{status}',
                        to_jsonb(CAST(? AS text))),
                    updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                  AND status = ?
                """,
                newStatus,
                newStatus,
                id,
                userId,
                expectedStatus) == 1;
    }

    private Optional<LearningPlanDraftRecord> find(
            String where,
            Object... arguments) {
        List<LearningPlanDraftRecord> records = jdbcTemplate.query(
                """
                SELECT
                    id,
                    run_id,
                    state_json::text AS state_json,
                    created_at,
                    updated_at
                FROM ai_learning_plan_draft
                """ + where + """
                LIMIT 1
                """,
                this::map,
                arguments);
        return records.stream().findFirst();
    }

    private LearningPlanDraftRecord map(
            ResultSet resultSet,
            int rowNum) throws SQLException {
        try {
            return new LearningPlanDraftRecord(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("run_id", UUID.class),
                    objectMapper.readValue(
                            resultSet.getString("state_json"),
                            LearningPlanState.class),
                    instant(resultSet, "created_at"),
                    instant(resultSet, "updated_at"));
        } catch (JsonProcessingException exception) {
            throw new SQLException(
                    "Unable to read learning plan draft",
                    exception);
        }
    }

    private String writeJson(LearningPlanState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to write learning plan draft",
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
