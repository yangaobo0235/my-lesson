package com.yangaobo.ai.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.event.KnowledgeChangeEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(
        name = "ai.knowledge-sync.outbox-enabled",
        havingValue = "true")
public class AiOutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AiOutboxRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(KnowledgeChangeEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_outbox_event (
                    event_id, event_type, aggregate_id,
                    event_version, payload, status,
                    retry_count, next_retry_at, created_at
                )
                VALUES (?, ?, ?, ?, CAST(? AS JSON), 'PENDING', 0, now(), now())
                """,
                event.eventId().toString(),
                event.eventType(),
                event.aggregateId(),
                event.version(),
                writeJson(event));
    }

    public List<OutboxRow> findReady(int limit) {
        return jdbcTemplate.query(
                """
                SELECT event_id, payload, retry_count
                FROM ai_outbox_event
                WHERE status IN ('PENDING', 'FAILED')
                  AND next_retry_at <= now()
                ORDER BY created_at
                LIMIT ?
                """,
                (rs, rowNum) -> new OutboxRow(
                        UUID.fromString(rs.getString("event_id")),
                        rs.getString("payload"),
                        rs.getInt("retry_count")),
                limit);
    }

    public void markSent(UUID eventId) {
        jdbcTemplate.update(
                """
                UPDATE ai_outbox_event
                SET status = 'SENT', sent_at = now(), last_error = NULL
                WHERE event_id = ?
                """,
                eventId.toString());
    }

    public void markFailed(
            UUID eventId,
            int retryCount,
            String error) {
        long delaySeconds = Math.min(
                3600L,
                5L * (1L << Math.min(retryCount, 10)));
        jdbcTemplate.update(
                """
                UPDATE ai_outbox_event
                SET status = 'FAILED',
                    retry_count = ?,
                    next_retry_at = ?,
                    last_error = ?
                WHERE event_id = ?
                """,
                retryCount,
                LocalDateTime.now().plusSeconds(delaySeconds),
                truncate(error),
                eventId.toString());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox event", exception);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown error";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record OutboxRow(
            UUID eventId,
            String payload,
            int retryCount
    ) {
    }
}
