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
                    version,
                    previous_draft_id,
                    state_json,
                    validation_errors,
                    review_result,
                    search_attempts,
                    repair_attempts,
                    adjustment_request,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb),
                    CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, now(), now()
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
                state.version(),
                state.previousDraftId(),
                writeJson(state),
                writeJson(state.validationErrors()),
                writeJson(state.reviewResult()),
                state.searchAttempts(),
                state.repairAttempts(),
                state.adjustmentRequest(),
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

    public List<LearningPlanDraftRecord> findAllOwned(
            Long userId,
            int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, run_id, state_json::text AS state_json,
                       created_at, updated_at
                FROM ai_learning_plan_draft
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                this::map,
                userId,
                limit);
    }

    public UUID createAdjustmentRun(Long userId, UUID requestId) {
        List<UUID> existing = jdbcTemplate.query(
                """
                SELECT id FROM ai_agent_run
                WHERE conversation_id IS NULL
                  AND user_id = ?
                  AND request_id = ?
                  AND agent_name = 'learning_plan_graph'
                LIMIT 1
                """,
                (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                userId,
                requestId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO ai_agent_run (
                    id, conversation_id, user_id, agent_name, intent,
                    status, model_name, request_id, checkpoint_thread_id,
                    created_at
                ) VALUES (
                    ?, NULL, ?, 'learning_plan_graph', 'LEARNING_PLAN',
                    'RUNNING', 'graph-designer-reviewer', ?, ?, now()
                )
                """,
                runId,
                userId,
                requestId,
                runId.toString());
        return runId;
    }

    public void completeAdjustmentRun(
            UUID runId,
            LearningPlanState state) {
        jdbcTemplate.update(
                """
                UPDATE ai_agent_run
                SET status = 'SUCCEEDED',
                    model_call_count = ?,
                    termination_reason = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                state.modelCallCount(),
                state.terminationReason(),
                runId);
    }

    public List<LearningPlanDraftRecord> findVersionsOwned(
            UUID id,
            Long userId) {
        return jdbcTemplate.query(
                """
                WITH RECURSIVE ancestors AS (
                    SELECT id, run_id, state_json, created_at, updated_at,
                           previous_draft_id, version
                    FROM ai_learning_plan_draft
                    WHERE id = ? AND user_id = ?
                    UNION ALL
                    SELECT draft.id, draft.run_id, draft.state_json,
                           draft.created_at, draft.updated_at,
                           draft.previous_draft_id, draft.version
                    FROM ai_learning_plan_draft draft
                    JOIN ancestors child
                      ON child.previous_draft_id = draft.id
                    WHERE draft.user_id = ?
                ), root_draft AS (
                    SELECT * FROM ancestors
                    WHERE previous_draft_id IS NULL
                    LIMIT 1
                ), draft_chain AS (
                    SELECT * FROM root_draft
                    UNION ALL
                    SELECT draft.id, draft.run_id, draft.state_json,
                           draft.created_at, draft.updated_at,
                           draft.previous_draft_id, draft.version
                    FROM ai_learning_plan_draft draft
                    JOIN draft_chain parent
                      ON draft.previous_draft_id = parent.id
                    WHERE draft.user_id = ?
                )
                SELECT id, run_id, state_json::text AS state_json,
                       created_at, updated_at
                FROM draft_chain
                ORDER BY version
                """,
                this::map,
                id,
                userId,
                userId,
                userId);
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

    private String writeJson(Object state) {
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
