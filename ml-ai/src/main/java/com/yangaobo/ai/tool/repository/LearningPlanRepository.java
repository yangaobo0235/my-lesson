package com.yangaobo.ai.tool.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.tool.model.LearningPlan;
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
public class LearningPlanRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LearningPlanRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void supersedeActive(Long userId) {
        jdbcTemplate.update(
                """
                UPDATE ai_learning_plan
                SET status = 'SUPERSEDED',
                    updated_at = now()
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                """,
                userId);
    }

    public LearningPlan insert(
            Long userId,
            LearningPlan plan) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO ai_learning_plan (
                    id,
                    user_id,
                    goal,
                    status,
                    plan_json,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, 'ACTIVE', CAST(? AS jsonb), now(), now()
                )
                RETURNING
                    id,
                    goal,
                    status,
                    plan_json::text AS plan_json,
                    created_at,
                    updated_at
                """,
                this::map,
                plan.id(),
                userId,
                plan.goal(),
                writeJson(plan));
    }

    public Optional<LearningPlan> findLatestActive(Long userId) {
        List<LearningPlan> plans = jdbcTemplate.query(
                """
                SELECT
                    id,
                    goal,
                    status,
                    plan_json::text AS plan_json,
                    created_at,
                    updated_at
                FROM ai_learning_plan
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                ORDER BY created_at DESC
                LIMIT 1
                """,
                this::map,
                userId);
        return plans.stream().findFirst();
    }

    public List<LearningPlan> findAllByUser(Long userId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    goal,
                    status,
                    plan_json::text AS plan_json,
                    created_at,
                    updated_at
                FROM ai_learning_plan
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                this::map,
                userId,
                limit);
    }

    private LearningPlan map(ResultSet resultSet, int rowNum)
            throws SQLException {
        try {
            LearningPlan stored = objectMapper.readValue(
                    resultSet.getString("plan_json"),
                    LearningPlan.class);
            return new LearningPlan(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("goal"),
                    stored.availableMinutesPerDay(),
                    stored.estimatedWeeks(),
                    resultSet.getString("status"),
                    stored.courses(),
                    stored.dailyRoutine(),
                    instant(resultSet, "created_at"),
                    instant(resultSet, "updated_at"));
        } catch (JsonProcessingException exception) {
            throw new SQLException(
                    "Unable to read learning plan JSON",
                    exception);
        }
    }

    private String writeJson(LearningPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize learning plan",
                    exception);
        }
    }

    private Instant instant(
            ResultSet resultSet,
            String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(
                column,
                OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
