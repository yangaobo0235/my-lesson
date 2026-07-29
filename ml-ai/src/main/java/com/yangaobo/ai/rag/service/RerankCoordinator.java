package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Duration;
import java.time.Instant;

@Service
public class RerankCoordinator {

    private static final Logger log =
            LoggerFactory.getLogger(RerankCoordinator.class);

    private final NoOpRerankService noOpRerankService;
    private final DashScopeRerankService dashScopeRerankService;
    private final RagProperties properties;

    public RerankCoordinator(
            NoOpRerankService noOpRerankService,
            DashScopeRerankService dashScopeRerankService,
            RagProperties properties) {
        this.noOpRerankService = noOpRerankService;
        this.dashScopeRerankService = dashScopeRerankService;
        this.properties = properties;
    }

    public RerankOutcome rerank(
            String query,
            List<SearchHit> candidates) {
        int topN = Math.min(
                properties.getRerank().getTopN(),
                properties.getAnswerTopN());
        if (!properties.getRerank().isEnabled()) {
            return new RerankOutcome(
                    noOpRerankService.rerank(query, candidates, topN),
                    false,
                    false,
                    topN,
                    0L);
        }
        Instant startedAt = Instant.now();
        try {
            return new RerankOutcome(
                    dashScopeRerankService.rerank(query, candidates, topN),
                    true,
                    false,
                    topN,
                    elapsed(startedAt));
        } catch (RuntimeException exception) {
            log.warn(
                    "DashScope rerank failed, falling back to RRF: {}",
                    exception.getClass().getSimpleName());
            return new RerankOutcome(
                    noOpRerankService.rerank(query, candidates, topN),
                    false,
                    true,
                    topN,
                    elapsed(startedAt));
        }
    }

    private long elapsed(Instant startedAt) {
        return Math.max(0L, Duration.between(
                startedAt, Instant.now()).toMillis());
    }

    public record RerankOutcome(
            List<SearchHit> hits,
            boolean applied,
            boolean fallback,
            int topN,
            long latencyMillis
    ) {

        public RerankOutcome(List<SearchHit> hits, boolean applied) {
            this(hits, applied, false, hits == null ? 0 : hits.size(), 0L);
        }
    }
}
