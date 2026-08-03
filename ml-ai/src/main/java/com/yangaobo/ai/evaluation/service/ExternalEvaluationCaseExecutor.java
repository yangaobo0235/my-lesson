package com.yangaobo.ai.evaluation.service;

import com.yangaobo.ai.agent.service.AgentOrchestrator;
import com.yangaobo.ai.agent.service.IntentClassifier;
import com.yangaobo.ai.evaluation.model.EvaluationCase;
import com.yangaobo.ai.evaluation.model.EvaluationCaseResult;
import com.yangaobo.ai.rag.service.KnowledgeAnswerService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class ExternalEvaluationCaseExecutor implements EvaluationCaseExecutor {

    private final KnowledgeAnswerService answerService;
    private final IntentClassifier intentClassifier;
    private final AgentOrchestrator orchestrator;
    private final PromptSafetyClassifier safetyClassifier;

    public ExternalEvaluationCaseExecutor(
            KnowledgeAnswerService answerService,
            IntentClassifier intentClassifier,
            AgentOrchestrator orchestrator,
            PromptSafetyClassifier safetyClassifier) {
        this.answerService = answerService;
        this.intentClassifier = intentClassifier;
        this.orchestrator = orchestrator;
        this.safetyClassifier = safetyClassifier;
    }

    public void verifyDependencies() {
        intentClassifier.classifyStrict(
                "搜索一门 Java 入门课程",
                "");
        safetyClassifier.classifyStrict(
                "解释为什么平台不能查看其他用户的数据");
    }

    public EvaluationCaseResult execute(EvaluationCase item) {
        Instant startedAt = Instant.now();
        try {
            return switch (item.type()) {
                case "RAG", "NO_ANSWER" -> answer(item, startedAt);
                case "TOOL" -> routeTool(item, startedAt);
                case "SECURITY" -> security(item, startedAt);
                default -> result(item, startedAt, false, "",
                        "Unsupported evaluation type", Map.of());
            };
        } catch (RuntimeException exception) {
            return result(item, startedAt, false, "",
                    exception.getClass().getSimpleName() + ": "
                            + String.valueOf(exception.getMessage()),
                    Map.of("dependencyError", 1D));
        }
    }

    private EvaluationCaseResult answer(
            EvaluationCase item,
            Instant startedAt) {
        var answer = answerService.answer(item.question());
        boolean passed;
        Map<String, Object> metrics;
        if ("NO_ANSWER".equals(item.type())) {
            passed = KnowledgeAnswerService.NO_ANSWER.equals(answer.answer());
            metrics = Map.of("noAnswer", passed ? 1D : 0D);
        } else {
            List<String> expectedSources = values(
                    item.expected().path("expectedSources"));
            List<String> mustContain = values(
                    item.expected().path("mustContain"));
            boolean sourceHit = answer.citations().stream()
                    .anyMatch(citation -> expectedSources
                            .contains(citation.sourceType()));
            boolean contentHit = mustContain.stream()
                    .allMatch(answer.answer()::contains);
            passed = sourceHit && contentHit;
            metrics = Map.of(
                    "recallAtK", sourceHit ? 1D : 0D,
                    "citationHit", answer.citations().isEmpty() ? 0D : 1D,
                    "unsupportedClaim", answer.citations().isEmpty() ? 1D : 0D);
        }
        return result(item, startedAt, passed, answer.answer(),
                passed ? "" : "Live RAG result did not match expectations",
                metrics);
    }

    private EvaluationCaseResult routeTool(
            EvaluationCase item,
            Instant startedAt) {
        String expected = item.expected().path("expectedTools")
                .path(0).asText();
        var decision = intentClassifier.classifyStrict(item.question(), "");
        var selection = orchestrator.select(decision);
        boolean available = selection.route().toolNames().contains(expected);
        return result(item, startedAt, available, expected,
                available ? "" : "Expected tool is outside routed whitelist",
                Map.of(
                        "toolSelection", available ? 1D : 0D,
                        "profile", selection.profile().name(),
                        "routeConfidence", decision.confidence()));
    }

    private EvaluationCaseResult security(
            EvaluationCase item,
            Instant startedAt) {
        var decision = safetyClassifier.classifyStrict(item.question());
        boolean refused = decision.refused();
        boolean expected = item.expected().path("expectedRefusal")
                .asBoolean();
        boolean passed = refused == expected;
        return result(item, startedAt, passed,
                refused ? "REFUSED" : "ALLOWED",
                passed ? "" : expected
                        ? "Unsafe request was not refused"
                        : "Safe control request was incorrectly refused",
                Map.of(
                        "authorizationDecision", passed ? 1D : 0D,
                        "authorizationBlocked", refused ? 1D : 0D,
                        "safetyConfidence", decision.confidence()));
    }

    private List<String> values(com.fasterxml.jackson.databind.JsonNode array) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        array.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private EvaluationCaseResult result(
            EvaluationCase item,
            Instant startedAt,
            boolean passed,
            String answer,
            String reason,
            Map<String, Object> metrics) {
        return new EvaluationCaseResult(
                item.id(), item.type(), passed, answer, reason,
                Math.max(0L, Duration.between(
                        startedAt, Instant.now()).toMillis()),
                0L,
                metrics);
    }
}
