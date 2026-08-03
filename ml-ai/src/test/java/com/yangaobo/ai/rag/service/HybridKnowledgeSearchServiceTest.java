package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.observability.AiMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridKnowledgeSearchServiceTest {

    private final VectorKnowledgeSearchService vectorSearch =
            mock(VectorKnowledgeSearchService.class);
    private final KeywordKnowledgeSearchService keywordSearch =
            mock(KeywordKnowledgeSearchService.class);
    private final ReciprocalRankFusionService fusion =
            mock(ReciprocalRankFusionService.class);
    private final RerankCoordinator rerank =
            mock(RerankCoordinator.class);

    @Test
    void shouldUseVectorOnlyWhenKeywordSearchFails() {
        when(vectorSearch.search("java")).thenReturn(List.of());
        when(keywordSearch.search("java"))
                .thenThrow(new IllegalStateException("down"));
        prepareEmptyResult();

        assertThat(service().search("java").hits()).isEmpty();
    }

    @Test
    void shouldUseKeywordOnlyWhenVectorSearchFails() {
        when(vectorSearch.search("java"))
                .thenThrow(new IllegalStateException("down"));
        when(keywordSearch.search("java")).thenReturn(List.of());
        prepareEmptyResult();

        assertThat(service().search("java").hits()).isEmpty();
    }

    private void prepareEmptyResult() {
        when(fusion.fuse(eq(List.of()), eq(List.of()), anyInt()))
                .thenReturn(new ReciprocalRankFusionService.FusionResult(
                        List.of(),
                        0.0));
        when(rerank.rerank("java", List.of()))
                .thenReturn(new RerankCoordinator.RerankOutcome(
                        List.of(),
                        false));
    }

    private HybridKnowledgeSearchService service() {
        return new HybridKnowledgeSearchService(
                vectorSearch,
                keywordSearch,
                fusion,
                rerank,
                new RagProperties(),
                mock(AiMetrics.class));
    }
}
