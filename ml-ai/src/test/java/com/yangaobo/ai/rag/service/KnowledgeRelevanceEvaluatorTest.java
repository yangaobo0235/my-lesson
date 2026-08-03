package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.HybridSearchResult;
import com.yangaobo.ai.rag.model.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRelevanceEvaluatorTest {

    private final RagProperties properties = new RagProperties();
    private final KnowledgeRelevanceEvaluator evaluator =
            new KnowledgeRelevanceEvaluator(
                    properties,
                    new KeywordScoreCalculator());

    @Test
    void shouldRejectEmptyOrLowScoreResults() {
        assertThat(evaluator.isAnswerable(
                "火星移民政策",
                new HybridSearchResult(List.of(), 0.0, false)))
                .isFalse();
        assertThat(evaluator.isAnswerable(
                "火星移民政策",
                result("Java课程", 0.40)))
                .isFalse();
    }

    @Test
    void shouldAcceptStrongOrLexicallyRelatedEvidence() {
        assertThat(evaluator.isAnswerable(
                "如何学习Java",
                result("Java入门课程", 0.80)))
                .isTrue();
        assertThat(evaluator.isAnswerable(
                "Java课程有哪些",
                result("Java课程介绍", 0.60)))
                .isTrue();
    }

    private HybridSearchResult result(String text, double evidenceScore) {
        SearchHit hit = new SearchHit(
                "COURSE",
                "1",
                text,
                text,
                "http://localhost/course/1",
                0.03);
        return new HybridSearchResult(
                List.of(hit),
                evidenceScore,
                false);
    }
}
