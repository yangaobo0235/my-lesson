package com.yangaobo.ai.observability.repository;

import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.observability.model.RunTimeline;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RunTimelineRepository {

    private final JdbcTemplate jdbcTemplate;

    public RunTimelineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RunTimeline findOwned(UUID runId, Long userId) {
        List<RunSummary> runs = jdbcTemplate.query(
                """
                SELECT id, status, intent, agent_name,
                       agent_profile_version, prompt_version,
                       route_confidence, conservative_mode,
                       model_call_count, tool_call_count,
                       termination_reason, created_at, finished_at
                FROM ai_agent_run
                WHERE id = ? AND user_id = ?
                """,
                this::mapRun,
                runId,
                userId);
        if (runs.isEmpty()) {
            throw new BusinessOperationException(
                    "AI_RUN_NOT_FOUND",
                    "AI 运行记录不存在");
        }
        RunSummary run = runs.get(0);
        return new RunTimeline(
                run.id(),
                run.status(),
                run.intent(),
                run.profileName(),
                run.profileVersion(),
                run.promptVersion(),
                run.routeConfidence(),
                run.conservativeMode(),
                run.modelCallCount(),
                run.toolCallCount(),
                run.terminationReason(),
                run.createdAt(),
                run.finishedAt(),
                events(runId, userId));
    }

    private List<RunTimeline.Event> events(UUID runId, Long userId) {
        return jdbcTemplate.query(
                """
                SELECT event_type, event_name, event_status,
                       latency_ms, event_time
                FROM (
                    SELECT 'RUN_STARTED' AS event_type,
                           run.agent_name AS event_name,
                           'STARTED' AS event_status,
                           CAST(NULL AS BIGINT) AS latency_ms,
                           run.created_at AS event_time
                    FROM ai_agent_run run
                    WHERE run.id = ? AND run.user_id = ?
                    UNION ALL
                    SELECT CASE WHEN tool.write_operation
                                THEN 'WRITE_TOOL' ELSE 'READ_TOOL' END,
                           tool.tool_name,
                           tool.status,
                           tool.latency_ms,
                           tool.created_at
                    FROM ai_tool_call tool
                    JOIN ai_agent_run run ON run.id = tool.run_id
                    WHERE tool.run_id = ? AND run.user_id = ?
                    UNION ALL
                    SELECT 'RETRIEVAL',
                           CASE WHEN trace.rewritten_query_hash IS NULL
                                THEN 'hybrid_retrieval'
                                ELSE 'hybrid_retrieval_rewritten' END,
                           CASE WHEN trace.no_answer_reason IS NULL
                                THEN 'SUCCEEDED' ELSE 'NO_ANSWER' END,
                           CAST(NULL AS BIGINT),
                           trace.created_at
                    FROM ai_retrieval_trace trace
                    JOIN ai_agent_run run ON run.id = trace.run_id
                    WHERE trace.run_id = ? AND run.user_id = ?
                    UNION ALL
                    SELECT 'RUN_COMPLETED',
                           run.agent_name,
                           run.status,
                           run.latency_ms,
                           run.finished_at
                    FROM ai_agent_run run
                    WHERE run.id = ? AND run.user_id = ?
                      AND run.finished_at IS NOT NULL
                ) events
                ORDER BY event_time
                """,
                this::mapEvent,
                runId,
                userId,
                runId,
                userId,
                runId,
                userId,
                runId,
                userId);
    }

    private RunSummary mapRun(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new RunSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("status"),
                resultSet.getString("intent"),
                resultSet.getString("agent_name"),
                resultSet.getString("agent_profile_version"),
                resultSet.getString("prompt_version"),
                nullableDouble(resultSet, "route_confidence"),
                resultSet.getBoolean("conservative_mode"),
                resultSet.getInt("model_call_count"),
                resultSet.getInt("tool_call_count"),
                resultSet.getString("termination_reason"),
                instant(resultSet, "created_at"),
                instant(resultSet, "finished_at"));
    }

    private RunTimeline.Event mapEvent(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new RunTimeline.Event(
                resultSet.getString("event_type"),
                resultSet.getString("event_name"),
                resultSet.getString("event_status"),
                nullableLong(resultSet, "latency_ms"),
                instant(resultSet, "event_time"));
    }

    private static Long nullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet resultSet, String column)
            throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        OffsetDateTime value = resultSet.getObject(
                column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record RunSummary(
            UUID id,
            String status,
            String intent,
            String profileName,
            String profileVersion,
            String promptVersion,
            Double routeConfidence,
            boolean conservativeMode,
            int modelCallCount,
            int toolCallCount,
            String terminationReason,
            Instant createdAt,
            Instant finishedAt
    ) {
    }
}
