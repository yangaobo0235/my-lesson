package com.yangaobo.ai.sync.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.sync.model.KnowledgeChangeEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KnowledgeInboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeInboxRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean start(KnowledgeChangeEvent event) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO ai_inbox_event (
                    event_id, event_type, aggregate_id, event_version,
                    payload, status, updated_at
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'PROCESSING', now())
                ON CONFLICT (event_id) DO NOTHING
                """,
                event.eventId().toString(),
                event.eventType(),
                event.aggregateId(),
                event.version(),
                json(event));
        if (inserted == 1) {
            return true;
        }
        List<String> statuses = jdbcTemplate.query(
                "SELECT status FROM ai_inbox_event WHERE event_id = ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                event.eventId().toString());
        if (statuses.isEmpty() || !"FAILED".equals(statuses.get(0))) {
            return false;
        }
        return jdbcTemplate.update(
                """
                UPDATE ai_inbox_event
                SET status = 'PROCESSING', retry_count = retry_count + 1,
                    last_error = NULL, updated_at = now()
                WHERE event_id = ? AND status = 'FAILED'
                """,
                event.eventId().toString()) == 1;
    }

    public void succeeded(KnowledgeChangeEvent event, boolean skipped) {
        jdbcTemplate.update(
                """
                UPDATE ai_inbox_event
                SET status = ?, processed_at = now(), updated_at = now()
                WHERE event_id = ?
                """,
                skipped ? "SKIPPED" : "SUCCEEDED",
                event.eventId().toString());
    }

    public void failed(KnowledgeChangeEvent event, Throwable error) {
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        jdbcTemplate.update(
                """
                UPDATE ai_inbox_event
                SET status = 'FAILED', last_error = ?, updated_at = now()
                WHERE event_id = ?
                """,
                message.length() <= 500 ? message : message.substring(0, 500),
                event.eventId().toString());
    }

    private String json(KnowledgeChangeEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize knowledge event", exception);
        }
    }
}
