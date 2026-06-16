package com.yangaobo.ai.conversation.repository;

import com.yangaobo.ai.conversation.model.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationRepository {

    private static final String SELECT_COLUMNS = """
            id, user_id, title, status, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Conversation create(Long userId, String title) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO ai_conversation (
                    id,
                    user_id,
                    title,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, 'ACTIVE', now(), now())
                RETURNING
                """ + SELECT_COLUMNS,
                this::map,
                id,
                userId,
                title);
    }

    public Optional<Conversation> findOwned(UUID id, Long userId) {
        List<Conversation> results = jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_conversation
                WHERE id = ?
                  AND user_id = ?
                  AND status <> 'DELETED'
                """,
                this::map,
                id,
                userId);
        return results.stream().findFirst();
    }

    public List<Conversation> findByUser(Long userId) {
        return jdbcTemplate.query(
                """
                SELECT
                """ + SELECT_COLUMNS + """
                FROM ai_conversation
                WHERE user_id = ?
                  AND status <> 'DELETED'
                ORDER BY updated_at DESC, id DESC
                """,
                this::map,
                userId);
    }

    public void updateInitialTitle(UUID id, Long userId, String title) {
        jdbcTemplate.update(
                """
                UPDATE ai_conversation
                SET title = ?, updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'ACTIVE'
                  AND (title IS NULL OR title = '' OR title = '新对话')
                """,
                title,
                id,
                userId);
    }

    public void touch(UUID id) {
        jdbcTemplate.update(
                """
                UPDATE ai_conversation
                SET updated_at = now()
                WHERE id = ?
                """,
                id);
    }

    public boolean softDelete(UUID id, Long userId) {
        return jdbcTemplate.update(
                """
                UPDATE ai_conversation
                SET status = 'DELETED', updated_at = now()
                WHERE id = ?
                  AND user_id = ?
                  AND status <> 'DELETED'
                """,
                id,
                userId) == 1;
    }

    private Conversation map(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new Conversation(
                resultSet.getObject("id", UUID.class),
                resultSet.getLong("user_id"),
                resultSet.getString("title"),
                resultSet.getString("status"),
                resultSet.getObject(
                                "created_at",
                                OffsetDateTime.class)
                        .toInstant(),
                resultSet.getObject(
                                "updated_at",
                                OffsetDateTime.class)
                        .toInstant());
    }
}
