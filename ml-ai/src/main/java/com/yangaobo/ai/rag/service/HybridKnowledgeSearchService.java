package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.HybridSearchResult;
import com.yangaobo.ai.rag.model.RetrievalCandidate;
import com.yangaobo.ai.rag.model.SearchHit;
import com.yangaobo.ai.rag.model.RetrievalTrace;
import com.yangaobo.ai.rag.repository.RetrievalTraceRepository;
import com.yangaobo.ai.observability.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.MDC;

import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Service
public class HybridKnowledgeSearchService {

    private static final Logger log =
            LoggerFactory.getLogger(HybridKnowledgeSearchService.class);

    private final VectorKnowledgeSearchService vectorSearchService;
    private final KeywordKnowledgeSearchService keywordSearchService;
    private final ReciprocalRankFusionService fusionService;
    private final RerankCoordinator rerankCoordinator;
    private final RagProperties properties;
    private final AiMetrics aiMetrics;
    private final RagQueryRewriter queryRewriter;
    private final RetrievalTraceRepository traceRepository;

    @Autowired
    public HybridKnowledgeSearchService(
            VectorKnowledgeSearchService vectorSearchService,
            KeywordKnowledgeSearchService keywordSearchService,
            ReciprocalRankFusionService fusionService,
            RerankCoordinator rerankCoordinator,
            RagProperties properties,
            AiMetrics aiMetrics,
            RagQueryRewriter queryRewriter,
            RetrievalTraceRepository traceRepository) {
        this.vectorSearchService = vectorSearchService;
        this.keywordSearchService = keywordSearchService;
        this.fusionService = fusionService;
        this.rerankCoordinator = rerankCoordinator;
        this.properties = properties;
        this.aiMetrics = aiMetrics;
        this.queryRewriter = queryRewriter;
        this.traceRepository = traceRepository;
    }

    HybridKnowledgeSearchService(
            VectorKnowledgeSearchService vectorSearchService,
            KeywordKnowledgeSearchService keywordSearchService,
            ReciprocalRankFusionService fusionService,
            RerankCoordinator rerankCoordinator,
            RagProperties properties,
            AiMetrics aiMetrics) {
        this(vectorSearchService, keywordSearchService, fusionService,
                rerankCoordinator, properties, aiMetrics, null, null);
    }

    public HybridSearchResult search(String query) {
        Instant startedAt = Instant.now();
        Map<String, Long> latency = new LinkedHashMap<>();
        Instant vectorStarted = Instant.now();
        List<RetrievalCandidate> vectorHits = visible(safeVectorSearch(query));
        latency.put("vectorMs", elapsed(vectorStarted));
        Instant keywordStarted = Instant.now();
        List<RetrievalCandidate> keywordHits = visible(safeKeywordSearch(query));
        latency.put("keywordMs", elapsed(keywordStarted));
        String rewrittenQuery = "";
        if (vectorHits.isEmpty()
                && keywordHits.isEmpty()
                && queryRewriter != null) {
            rewrittenQuery = queryRewriter.rewrite(query);
            if (!rewrittenQuery.isBlank()
                    && !rewrittenQuery.equalsIgnoreCase(query.strip())) {
                Instant rewriteSearchStarted = Instant.now();
                vectorHits = visible(safeVectorSearch(rewrittenQuery));
                keywordHits = visible(safeKeywordSearch(rewrittenQuery));
                latency.put("rewriteSearchMs", elapsed(rewriteSearchStarted));
            }
        }
        Instant fusionStarted = Instant.now();
        ReciprocalRankFusionService.FusionResult fused =
                fusionService.fuse(
                        vectorHits,
                        keywordHits,
                        properties.getFusionTopK());
        latency.put("fusionMs", elapsed(fusionStarted));
        List<SearchHit> candidates = fused.candidates().stream()
                .map(RetrievalCandidate::hit)
                .toList();
        String effectiveQuery = rewrittenQuery.isBlank()
                ? query
                : rewrittenQuery;
        RerankCoordinator.RerankOutcome reranked =
                rerankCoordinator.rerank(effectiveQuery, candidates);
        latency.put("rerankMs", reranked.latencyMillis());
        UUID traceId = UUID.randomUUID();
        HybridSearchResult result = new HybridSearchResult(
                reranked.hits(),
                fused.highestEvidenceScore(),
                reranked.applied(),
                traceId,
                vectorHits.size(),
                keywordHits.size(),
                fused.candidates().size(),
                reranked.fallback(),
                !rewrittenQuery.isBlank());
        aiMetrics.retrieval(
                Duration.between(startedAt, Instant.now()),
                result.hits().isEmpty());
        saveTrace(result, query, rewrittenQuery, latency);
        return result;
    }

    public void markNoAnswer(HybridSearchResult result, String reason) {
        if (traceRepository == null || result == null) {
            return;
        }
        try {
            traceRepository.markNoAnswer(result.traceId(), reason);
        } catch (RuntimeException exception) {
            log.warn("Unable to update retrieval trace: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private void saveTrace(
            HybridSearchResult result,
            String query,
            String rewrittenQuery,
            Map<String, Long> latency) {
        if (traceRepository == null) {
            return;
        }
        try {
            traceRepository.save(new RetrievalTrace(
                    result.traceId(),
                    runId(),
                    hash(query),
                    rewrittenQuery.isBlank() ? null : hash(rewrittenQuery),
                    result.vectorCandidateCount(),
                    result.keywordCandidateCount(),
                    result.fusedCandidateCount(),
                    result.reranked(),
                    result.rerankFallback(),
                    result.hits().size(),
                    result.hits().isEmpty() ? "NO_CANDIDATES" : null,
                    Map.copyOf(latency)));
        } catch (RuntimeException exception) {
            log.warn("Unable to persist retrieval trace: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private UUID runId() {
        try {
            String value = MDC.get("runId");
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private long elapsed(Instant startedAt) {
        return Math.max(0L, Duration.between(
                startedAt, Instant.now()).toMillis());
    }

    private List<RetrievalCandidate> safeVectorSearch(String query) {
        try {
            return vectorSearchService.search(query);
        } catch (RuntimeException exception) {
            log.warn(
                    "Vector search unavailable, using keyword search only: {}",
                    exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<RetrievalCandidate> safeKeywordSearch(String query) {
        try {
            return keywordSearchService.search(query);
        } catch (RuntimeException exception) {
            log.warn(
                    "Keyword search unavailable, using vector search only: {}",
                    exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<RetrievalCandidate> visible(
            List<RetrievalCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate != null
                        && candidate.hit() != null
                        && candidate.hit().version() > 0
                        && Set.of("ACTIVE", "PUBLISHED")
                                .contains(candidate.hit().visibilityStatus()))
                .toList();
    }
}
