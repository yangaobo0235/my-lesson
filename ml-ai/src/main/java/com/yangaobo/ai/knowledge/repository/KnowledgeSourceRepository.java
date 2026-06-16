package com.yangaobo.ai.knowledge.repository;

import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import com.yangaobo.ai.knowledge.model.KnowledgeSourceKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class KnowledgeSourceRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SourceState> find(String sourceType, String sourceId) {
        List<SourceState> states = jdbcTemplate.query(
                """
                SELECT content_hash, version, status
                FROM ai_knowledge_source
                WHERE source_type = ? AND source_id = ?
                """,
                (resultSet, rowNum) -> new SourceState(
                        resultSet.getString("content_hash"),
                        resultSet.getLong("version"),
                        resultSet.getString("status")),
                sourceType,
                sourceId);
        return states.stream().findFirst();
    }

    public long countChunks(String sourceType, String sourceId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM vector_store
                WHERE metadata->>'source_type' = ?
                  AND metadata->>'source_id' = ?
                """,
                Long.class,
                sourceType,
                sourceId);
        return count == null ? 0L : count;
    }

    public void upsert(KnowledgeDocument source) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_source (
                    source_type,
                    source_id,
                    title,
                    source_url,
                    content_hash,
                    version,
                    status,
                    indexed_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now())
                ON CONFLICT (source_type, source_id)
                DO UPDATE SET
                    title = EXCLUDED.title,
                    source_url = EXCLUDED.source_url,
                    content_hash = EXCLUDED.content_hash,
                    version = EXCLUDED.version,
                    status = 'ACTIVE',
                    indexed_at = now(),
                    updated_at = now()
                WHERE ai_knowledge_source.version <= EXCLUDED.version
                """,
                source.sourceType(),
                source.sourceId(),
                source.title(),
                source.sourceUrl(),
                source.contentHash(),
                source.version());
    }

    public List<KnowledgeSourceKey> findActiveKeys() {
        return jdbcTemplate.query(
                """
                SELECT source_type, source_id
                FROM ai_knowledge_source
                WHERE status = 'ACTIVE'
                """,
                (resultSet, rowNum) -> new KnowledgeSourceKey(
                        resultSet.getString("source_type"),
                        resultSet.getString("source_id")));
    }

    public void markDeleted(KnowledgeSourceKey source) {
        long version = find(source.sourceType(), source.sourceId())
                .map(SourceState::version)
                .orElse(1L);
        markDeleted(source, version);
    }

    public void markDeleted(KnowledgeSourceKey source, long version) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_source (
                    source_type, source_id, title, content_hash, version,
                    status, updated_at
                )
                VALUES (?, ?, ?, '', ?, 'DELETED', now())
                ON CONFLICT (source_type, source_id)
                DO UPDATE SET
                    version = EXCLUDED.version,
                    status = 'DELETED',
                    updated_at = now()
                WHERE ai_knowledge_source.version <= EXCLUDED.version
                """,
                source.sourceType(),
                source.sourceId(),
                source.sourceType() + ":" + source.sourceId(),
                version);
    }

    public Map<String, Long> countActiveSourcesByType() {
        return countByType(
                """
                SELECT source_type, count(*) AS total
                FROM ai_knowledge_source
                WHERE status = 'ACTIVE'
                GROUP BY source_type
                ORDER BY source_type
                """);
    }

    public Map<String, Long> countVectorsByType() {
        return countByType(
                """
                SELECT COALESCE(metadata->>'source_type', 'UNKNOWN') AS source_type,
                       count(*) AS total
                FROM vector_store
                GROUP BY metadata->>'source_type'
                ORDER BY source_type
                """);
    }

    private Map<String, Long> countByType(String sql) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<TypeCount> rows = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new TypeCount(
                        resultSet.getString("source_type"),
                        resultSet.getLong("total")));
        rows.forEach(row -> result.put(row.sourceType(), row.total()));
        return Map.copyOf(result);
    }

    public record SourceState(
            String contentHash,
            long version,
            String status
    ) {
    }

    private record TypeCount(
            String sourceType,
            long total
    ) {
    }
}
