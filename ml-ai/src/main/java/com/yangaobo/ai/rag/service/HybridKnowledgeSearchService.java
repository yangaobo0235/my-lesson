package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.HybridSearchResult;
import com.yangaobo.ai.rag.model.RetrievalCandidate;
import com.yangaobo.ai.rag.model.SearchHit;
import com.yangaobo.ai.observability.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Duration;
import java.time.Instant;

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

    public HybridKnowledgeSearchService(
            VectorKnowledgeSearchService vectorSearchService,
            KeywordKnowledgeSearchService keywordSearchService,
            ReciprocalRankFusionService fusionService,
            RerankCoordinator rerankCoordinator,
            RagProperties properties,
            AiMetrics aiMetrics) {
        this.vectorSearchService = vectorSearchService;
        this.keywordSearchService = keywordSearchService;
        this.fusionService = fusionService;
        this.rerankCoordinator = rerankCoordinator;
        this.properties = properties;
        this.aiMetrics = aiMetrics;
    }

    public HybridSearchResult search(String query) {
        Instant startedAt = Instant.now();
        List<RetrievalCandidate> vectorHits = safeVectorSearch(query);
        List<RetrievalCandidate> keywordHits =
                safeKeywordSearch(query);
        ReciprocalRankFusionService.FusionResult fused =
                fusionService.fuse(
                        vectorHits,
                        keywordHits,
                        properties.getFusionTopK());
        List<SearchHit> candidates = fused.candidates().stream()
                .map(RetrievalCandidate::hit)
                .toList();
        RerankCoordinator.RerankOutcome reranked =
                rerankCoordinator.rerank(query, candidates);
        HybridSearchResult result = new HybridSearchResult(
                reranked.hits(),
                fused.highestEvidenceScore(),
                reranked.applied());
        aiMetrics.retrieval(
                Duration.between(startedAt, Instant.now()),
                result.hits().isEmpty());
        return result;
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
}
