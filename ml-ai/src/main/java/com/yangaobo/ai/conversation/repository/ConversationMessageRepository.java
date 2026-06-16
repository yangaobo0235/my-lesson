package com.yangaobo.ai.conversation.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.conversation.model.ConversationMessage;
import com.yangaobo.ai.rag.model.Citation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
public class ConversationMessageRepository {

    private static final String SELECT_COLUMNS = """
            id,
            conversation_id,
            role,
            content,
            citations,
            request_id,
            trace_id,
            summary_until,
            summary_until_message_id,
            created_at
            """;
    private static final TypeReference<List<Citation>> CITATION_LIST_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ConversationMessageRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ConversationMessage insertUser(
            UUID conversationId,
            String content,
            UUID requestId,
            String traceId) {
        return insert(
                conversationId,
                "USER",
                content,
                List.of(),
                requestId,
                traceId,
                null,
                null);
    }

    public ConversationMessage insertAssistant(
            UUID conversationId,
            String content,
            List<Citation> citations,
            UUID requestId,
            String traceId) {
        return insert(
                conversationId,
                "ASSISTANT",
                content,
                citations,
                requestId,
                traceId,
                null,
                null);
    }

    public ConversationMessage insertSummary(
            UUID conversationId,
            String content,
            Instant summaryUntil,
            UUID summaryUntilMessageId,
            String traceId) {
        return insert(
                conversationId,
                "SYSTEM_SUMMARY",
                content,
                List.of(),
                null,
                traceId,
                summaryUntil,
                summaryUntilMessageId);
    }

    public List<ConversationMessage> findVisible(
            UUID conversationId,
            int limit) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM (
                    SELECT
                """ + SELECT_COLUMNS + """
                    FROM ai_message
                    WHERE conversation_id = ?
                      AND role IN ('USER', 'ASSISTANT')
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                ) recent
                ORDER BY created_at ASC, id ASC
                """,
                this::map,
                conversationId,
                limit);
    }

    public List<ConversationMessage> findRecentDialogMessages(
            UUID conversationId,
            UUID excludedMessageId,
            int limit) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM (
                    SELECT
                """ + SELECT_COLUMNS + """
                    FROM ai_message
                    WHERE conversation_id = ?
                      AND role IN ('USER', 'ASSISTANT')
                      AND id <> ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                ) recent
                ORDER BY created_at ASC, id ASC
                """,
                this::map,
                conversationId,
                excludedMessageId,
                limit);
    }

    public ConversationMessage findLatestSummary(UUID conversationId) {
        List<ConversationMessage> summaries = jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_message
                WHERE conversation_id = ?
                  AND role = 'SYSTEM_SUMMARY'
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                this::map,
                conversationId);
        return summaries.stream().findFirst().orElse(null);
    }

    public List<ConversationMessage> findMessagesForSummary(
            UUID conversationId,
            Instant summarizedUntil,
            UUID summarizedUntilMessageId,
            ConversationMessage recentCutoff,
            int limit) {
        if (summarizedUntil == null) {
            return jdbcTemplate.query(
                    """
                    SELECT
                    """ + SELECT_COLUMNS + """
                    FROM ai_message
                    WHERE conversation_id = ?
                      AND role IN ('USER', 'ASSISTANT')
                      AND (created_at, id) < (?, ?)
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    """,
                    this::map,
                    conversationId,
                    Timestamp.from(recentCutoff.createdAt()),
                    recentCutoff.id(),
                    limit);
        }
        return jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_message
                WHERE conversation_id = ?
                  AND role IN ('USER', 'ASSISTANT')
                  AND (created_at, id) > (?, ?)
                  AND (created_at, id) < (?, ?)
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                """,
                this::map,
                conversationId,
                Timestamp.from(summarizedUntil),
                summarizedUntilMessageId,
                Timestamp.from(recentCutoff.createdAt()),
                recentCutoff.id(),
                limit);
    }

    private ConversationMessage insert(
            UUID conversationId,
            String role,
            String content,
            List<Citation> citations,
            UUID requestId,
            String traceId,
            Instant summaryUntil,
            UUID summaryUntilMessageId) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO ai_message (
                    id,
                    conversation_id,
                    role,
                    content,
                    citations,
                    request_id,
                    trace_id,
                    summary_until,
                    summary_until_message_id,
                    created_at
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, now())
                RETURNING
                """ + SELECT_COLUMNS,
                this::map,
                id,
                conversationId,
                role,
                content,
                writeCitations(citations),
                requestId,
                traceId,
                summaryUntil == null ? null : Timestamp.from(summaryUntil),
                summaryUntilMessageId);
    }

    private ConversationMessage map(ResultSet resultSet, int rowNum)
            throws SQLException {
        OffsetDateTime summaryUntil = resultSet.getObject(
                "summary_until",
                OffsetDateTime.class);
        return new ConversationMessage(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("conversation_id", UUID.class),
                resultSet.getString("role"),
                resultSet.getString("content"),
                readCitations(resultSet.getString("citations")),
                resultSet.getObject("request_id", UUID.class),
                resultSet.getString("trace_id"),
                summaryUntil == null ? null : summaryUntil.toInstant(),
                resultSet.getObject(
                        "summary_until_message_id",
                        UUID.class),
                resultSet.getObject(
                                "created_at",
                                OffsetDateTime.class)
                        .toInstant());
    }

    private String writeCitations(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize message citations",
                    exception);
        }
    }

    private List<Citation> readCitations(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<Citation> citations =
                    objectMapper.readValue(value, CITATION_LIST_TYPE);
            return citations == null
                    ? List.of()
                    : Collections.unmodifiableList(citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to deserialize message citations",
                    exception);
        }
    }
}
