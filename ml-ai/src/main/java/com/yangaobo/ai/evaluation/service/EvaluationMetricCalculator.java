package com.yangaobo.ai.evaluation.service;

import com.yangaobo.ai.evaluation.model.EvaluationCaseResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EvaluationMetricCalculator {

    public Map<String, Double> calculate(
            List<EvaluationCaseResult> results) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("overallPassRate", passRate(results));
        metrics.put("ragPassRate", passRate(type(results, "RAG")));
        double toolPassRate = passRate(type(results, "TOOL"));
        metrics.put("toolRouteCoverage", toolPassRate);
        metrics.put("toolSelectionAccuracy", toolPassRate);
        metrics.put("authorizationBlockRate",
                passRate(type(results, "SECURITY")));
        metrics.put("noAnswerAccuracy",
                passRate(type(results, "NO_ANSWER")));
        metrics.put("citationHitRate", averageMetric(
                type(results, "RAG"), "citationHit"));
        metrics.put("unsupportedClaimRate", averageMetric(
                type(results, "RAG"), "unsupportedClaim"));
        metrics.put("recallAtK", averageMetric(
                type(results, "RAG"), "recallAtK"));
        metrics.put("mrr", averageMetric(type(results, "RAG"), "mrr"));
        metrics.put("ragP95LatencyMs", (double) p95Latency(type(results, "RAG")));
        metrics.put("toolP95LatencyMs", (double) p95Latency(type(results, "TOOL")));
        metrics.put("securityP95LatencyMs", (double) p95Latency(type(results, "SECURITY")));
        metrics.put("noAnswerP95LatencyMs", (double) p95Latency(type(results, "NO_ANSWER")));
        return Map.copyOf(metrics);
    }

    public long p95Latency(List<EvaluationCaseResult> results) {
        List<Long> sorted = results.stream()
                .map(EvaluationCaseResult::latencyMs)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0,
                (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }

    private List<EvaluationCaseResult> type(
            List<EvaluationCaseResult> results,
            String type) {
        return results.stream()
                .filter(result -> type.equals(result.caseType()))
                .toList();
    }

    private double passRate(List<EvaluationCaseResult> results) {
        return results.isEmpty() ? 0D : results.stream()
                .filter(EvaluationCaseResult::passed)
                .count() / (double) results.size();
    }

    private double averageMetric(
            List<EvaluationCaseResult> results,
            String name) {
        return results.stream()
                .map(result -> result.metrics().get(name))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0D);
    }
}
