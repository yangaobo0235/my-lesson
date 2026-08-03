package com.yangaobo.ai.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class EvaluationThresholdGate {

    private final ObjectMapper objectMapper;

    public EvaluationThresholdGate(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GateResult evaluate(Map<String, Double> metrics, long p95Latency) {
        JsonNode config = config();
        boolean active = config.path("baselineRequired").asBoolean(false);
        if (!active) {
            return new GateResult(false, true,
                    "基线尚未锁定，本次报告仅建立可复现基线");
        }
        boolean passed = metrics.getOrDefault("citationHitRate", 0D)
                >= config.path("citationHitRate").asDouble()
                && metrics.getOrDefault("toolSelectionAccuracy", 0D)
                >= config.path("toolSelectionAccuracy").asDouble()
                && metrics.getOrDefault("authorizationBlockRate", 0D)
                >= config.path("authorizationBlockRate").asDouble()
                && metrics.getOrDefault("noAnswerAccuracy", 0D)
                >= config.path("noAnswerAccuracy").asDouble()
                && p95Latency <= config.path("localP95LatencyMillis")
                        .asLong(Long.MAX_VALUE);
        return new GateResult(true, passed,
                passed ? "通过回归门禁" : "未达到已锁定基线阈值");
    }

    private JsonNode config() {
        try {
            return objectMapper.readTree(new ClassPathResource(
                    "evaluation/regression-thresholds.json")
                    .getInputStream());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load evaluation thresholds", exception);
        }
    }

    public record GateResult(
            boolean active,
            boolean passed,
            String reason
    ) {
    }
}
