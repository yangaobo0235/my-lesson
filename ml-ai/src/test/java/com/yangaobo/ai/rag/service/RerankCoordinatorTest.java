package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RerankCoordinatorTest {

    @Test
    void shouldFallBackToRrfWhenDashScopeFails() {
        RagProperties properties = new RagProperties();
        properties.getRerank().setEnabled(true);
        NoOpRerankService noOp = mock(NoOpRerankService.class);
        DashScopeRerankService dashScope =
                mock(DashScopeRerankService.class);
        List<SearchHit> candidates = List.of(hit());
        when(dashScope.rerank(eq("Java"), anyList(), anyInt()))
                .thenThrow(new IllegalStateException("remote failure"));
        when(noOp.rerank(eq("Java"), anyList(), anyInt()))
                .thenReturn(candidates);

        RerankCoordinator.RerankOutcome result =
                new RerankCoordinator(noOp, dashScope, properties)
                        .rerank("Java", candidates);

        assertThat(result.applied()).isFalse();
        assertThat(result.hits()).isEqualTo(candidates);
    }

    private SearchHit hit() {
        return new SearchHit(
                "COURSE",
                "1",
                "Java",
                "Java课程",
                "http://localhost/course/1",
                0.03);
    }
}
