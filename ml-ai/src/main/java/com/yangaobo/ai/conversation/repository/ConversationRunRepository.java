package com.yangaobo.ai.conversation.repository;

import com.yangaobo.ai.conversation.model.ConversationRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationRunRepository {

    private static final String SELECT_COLUMNS = """
            id,
            conversation_id,
            request_id,
            status,
            user_message_id,
            assistant_message_id,
            trace_id,
            error_message
            """;

    private final JdbcTemplate jdbcTemplate;

    public ConversationRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConversationRun insert(
            UUID id,
            UUID conversationId,
            Long userId,
            UUID requestId,
            UUID userMessageId,
            String traceId,
            String modelName) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO ai_agent_run (
                    id,
                    conversation_id,
                    user_id,
                    agent_name,
                    intent,
                    status,
                    model_name,
                    trace_id,
                    request_id,
                    user_message_id,
                    created_at
                )
                VALUES (
                    ?, ?, ?, 'assistant-conversation', 'ASSISTANT',
                    'PENDING', ?, ?, ?, ?, now()
                )
                RETURNING
                """ + SELECT_COLUMNS,
                this::map,
                id,
                conversationId,
                userId,
                modelName,
                traceId,
                requestId,
                userMessageId);
    }

    public Optional<ConversationRun> findByRequest(
            UUID conversationId,
            UUID requestId) {
        List<ConversationRun> results = jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_agent_run
                WHERE conversation_id = ?
                  AND request_id = ?
                """,
                this::map,
                conversationId,
                requestId);
        return results.stream().findFirst();
    }

    public void markRunning(UUID runId) {
        jdbcTemplate.update(
                """
                UPDATE ai_agent_run
                SET status = 'RUNNING'
                WHERE id = ?
                """,
                runId);
    }

    public void updateIntent(UUID runId, String intent) {
        jdbcTemplate.update(
                """
                UPDATE ai_agent_run
                SET intent = ?
                WHERE id = ?
                """,
                intent,
                runId);
    }

    public void updateRoute(
            UUID runId,
            String profileName,
            String profileVersion,
            String intent,
            double confidence,
            boolean conservative) {
        jdbcTemplate.update(
                """
                UPDATE ai_agent_run
                SET agent_name = ?,
                    agent_profile_version = ?,
                    intent = ?,
                    route_confidence = ?,
                    conservative_mode = ?,
                    prompt_version = ?
                WHERE id = ?
                """,
                profileName,
                profileVersion,
                intent,
                confidence,
                conservative,
                profileName + "-" + profileVersion,
                runId);
    }

    public void updateToolCallCount(UUID runId, int toolCallCount) {
        jdbcTemplate.update(
                "UPDATE ai_agent_run SET tool_call_count = ? WHERE id = ?",
                Math.max(0, toolCallCount),
                runId);
    }

    public void markSucceeded(
            UUID runId,
            UUID assistantMessageId,
            long latencyMillis) {
        jdbcTemplate.update(
                """
                UPDATE ai_agent_run
                SET status = 'SUCCEEDED',
                    assistant_message_id = ?,
                    latency_ms = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                assistantMessageId,
                latencyMillis,
                runId);
    }

    public void markFailed(
            UUID runId,
            String errorMessage,
            long latencyMillis) {
        jdbcTemplate.update(
                """
                UPDATE ai_agent_run
                SET status = 'FAILED',
                    error_message = ?,
                    latency_ms = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                errorMessage,
                latencyMillis,
                runId);
    }

    private ConversationRun map(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new ConversationRun(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("conversation_id", UUID.class),
                resultSet.getObject("request_id", UUID.class),
                resultSet.getString("status"),
                resultSet.getObject("user_message_id", UUID.class),
                resultSet.getObject("assistant_message_id", UUID.class),
                resultSet.getString("trace_id"),
                resultSet.getString("error_message"));
    }
}
