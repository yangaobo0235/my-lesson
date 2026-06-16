package com.yangaobo.ai.evaluation.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.evaluation.model.EvaluationResultView;
import com.yangaobo.ai.evaluation.model.EvaluationSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class EvaluationQueryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EvaluationQueryRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public EvaluationSummary summary() {
        Long caseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_eval_case WHERE enabled = true",
                Long.class);
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    count(*) AS result_count,
                    count(*) FILTER (WHERE passed) AS passed_count,
                    avg(NULLIF(metrics ->> 'latencyMs', '')::double precision)
                        AS average_latency_ms,
                    avg(NULLIF(metrics ->> 'tokenUsage', '')::double precision)
                        AS average_token_usage,
                    (array_agg(model_name ORDER BY created_at DESC))[1]
                        AS latest_model,
                    max(created_at) AS latest_run_at
                FROM ai_eval_result
                """,
                (resultSet, rowNum) -> {
                    long results = resultSet.getLong("result_count");
                    long passed = resultSet.getLong("passed_count");
                    return new EvaluationSummary(
                            caseCount == null ? 0L : caseCount,
                            results,
                            passed,
                            results == 0 ? 0D : (double) passed / results,
                            nullableDouble(resultSet, "average_latency_ms"),
                            nullableDouble(resultSet, "average_token_usage"),
                            resultSet.getString("latest_model"),
                            instant(resultSet, "latest_run_at"));
                });
    }

    public List<EvaluationResultView> recentResults(int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                    result.id,
                    result.case_id,
                    evaluation_case.case_type,
                    evaluation_case.question,
                    result.model_name,
                    result.answer,
                    result.metrics::text AS metrics,
                    result.passed,
                    result.created_at
                FROM ai_eval_result result
                JOIN ai_eval_case evaluation_case
                  ON evaluation_case.id = result.case_id
                ORDER BY result.created_at DESC
                LIMIT ?
                """,
                this::map,
                limit);
    }

    private EvaluationResultView map(
            ResultSet resultSet,
            int rowNum) throws SQLException {
        return new EvaluationResultView(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getObject("case_id", java.util.UUID.class),
                resultSet.getString("case_type"),
                resultSet.getString("question"),
                resultSet.getString("model_name"),
                resultSet.getString("answer"),
                readJson(resultSet.getString("metrics")),
                resultSet.getBoolean("passed"),
                instant(resultSet, "created_at"));
    }

    private JsonNode readJson(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to read evaluation metrics", exception);
        }
    }

    private static Double nullableDouble(
            ResultSet resultSet,
            String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(
            ResultSet resultSet,
            String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(
                column,
                OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
