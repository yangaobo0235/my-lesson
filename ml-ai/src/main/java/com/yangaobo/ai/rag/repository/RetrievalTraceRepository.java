package com.yangaobo.ai.rag.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.rag.model.RetrievalTrace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RetrievalTraceRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RetrievalTraceRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(RetrievalTrace trace) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_retrieval_trace (
                    id, run_id, query_hash, rewritten_query_hash,
                    vector_candidate_count, keyword_candidate_count,
                    fused_candidate_count, rerank_applied, rerank_fallback,
                    final_hit_count, no_answer_reason, latency_breakdown,
                    created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), now()
                )
                """,
                trace.id(),
                trace.runId(),
                trace.queryHash(),
                trace.rewrittenQueryHash(),
                trace.vectorCandidateCount(),
                trace.keywordCandidateCount(),
                trace.fusedCandidateCount(),
                trace.rerankApplied(),
                trace.rerankFallback(),
                trace.finalHitCount(),
                trace.noAnswerReason(),
                json(trace.latencyBreakdown()));
    }

    public void markNoAnswer(UUID id, String reason) {
        if (id != null) {
            jdbcTemplate.update(
                    "UPDATE ai_retrieval_trace SET no_answer_reason = ? WHERE id = ?",
                    reason,
                    id);
        }
    }

    public List<RetrievalTrace> findByRunOwned(UUID runId, Long userId) {
        return jdbcTemplate.query(
                """
                SELECT trace.*
                FROM ai_retrieval_trace trace
                JOIN ai_agent_run run ON run.id = trace.run_id
                WHERE trace.run_id = ?
                  AND run.user_id = ?
                ORDER BY trace.created_at
                """,
                this::map,
                runId,
                userId);
    }

    private RetrievalTrace map(ResultSet resultSet, int rowNum)
            throws SQLException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Long> latency = objectMapper.readValue(
                    resultSet.getString("latency_breakdown"),
                    Map.class);
            return new RetrievalTrace(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("run_id", UUID.class),
                    resultSet.getString("query_hash"),
                    resultSet.getString("rewritten_query_hash"),
                    resultSet.getInt("vector_candidate_count"),
                    resultSet.getInt("keyword_candidate_count"),
                    resultSet.getInt("fused_candidate_count"),
                    resultSet.getBoolean("rerank_applied"),
                    resultSet.getBoolean("rerank_fallback"),
                    resultSet.getInt("final_hit_count"),
                    resultSet.getString("no_answer_reason"),
                    latency);
        } catch (JsonProcessingException exception) {
            throw new SQLException(
                    "Unable to read retrieval latency breakdown",
                    exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize retrieval trace", exception);
        }
    }
}
