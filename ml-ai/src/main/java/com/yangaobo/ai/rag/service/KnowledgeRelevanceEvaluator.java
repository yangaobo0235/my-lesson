package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.HybridSearchResult;
import com.yangaobo.ai.rag.model.SearchHit;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class KnowledgeRelevanceEvaluator {

    private final RagProperties properties;
    private final KeywordScoreCalculator scoreCalculator;

    public KnowledgeRelevanceEvaluator(
            RagProperties properties,
            KeywordScoreCalculator scoreCalculator) {
        this.properties = properties;
        this.scoreCalculator = scoreCalculator;
    }

    public boolean isAnswerable(
            String query,
            HybridSearchResult result) {
        if (result.hits().isEmpty()
                || result.highestEvidenceScore()
                < properties.getMinimumRelevantScore()) {
            return false;
        }
        if (result.highestEvidenceScore()
                >= properties.getStrongRelevantScore()) {
            return true;
        }

        Set<String> queryTerms = scoreCalculator.terms(query);
        if (queryTerms.isEmpty()) {
            return false;
        }
        Set<String> contentTerms = new HashSet<>();
        result.hits().stream()
                .limit(properties.getAnswerTopN())
                .map(hit -> hit.title() + " " + hit.snippet())
                .map(scoreCalculator::terms)
                .forEach(contentTerms::addAll);
        return queryTerms.stream().anyMatch(contentTerms::contains);
    }
}
