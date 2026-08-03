package com.yangaobo.ai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.agent.config.AgentProperties;
import com.yangaobo.ai.agent.service.AgentOrchestrator;
import com.yangaobo.ai.agent.service.IntentClassifier;
import com.yangaobo.ai.agent.service.IntentRoutingPolicy;
import com.yangaobo.ai.evaluation.service.DeterministicEvaluationCaseExecutor;
import com.yangaobo.ai.evaluation.service.EvaluationDatasetLoader;
import com.yangaobo.ai.evaluation.service.EvaluationMetricCalculator;
import com.yangaobo.ai.evaluation.service.EvaluationReportWriter;
import com.yangaobo.ai.evaluation.service.EvaluationRunnerService;
import com.yangaobo.ai.evaluation.service.EvaluationThresholdGate;
import com.yangaobo.ai.evaluation.service.ExternalEvaluationCaseExecutor;
import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.service.CitationService;
import com.yangaobo.ai.rag.service.ReciprocalRankFusionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeterministicEvaluationRunnerTest {

    @TempDir
    Path reportDirectory;

    @Test
    void shouldExecuteAllCasesAndWriteReports() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        IntentClassifier classifier = new IntentClassifier(
                mock(org.springframework.ai.chat.client.ChatClient.Builder.class),
                new AgentProperties(),
                new org.springframework.core.task.support.TaskExecutorAdapter(
                        Runnable::run));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                new IntentRoutingPolicy(new AgentProperties()));
        DeterministicEvaluationCaseExecutor executor =
                new DeterministicEvaluationCaseExecutor(
                        classifier,
                        orchestrator,
                        new ReciprocalRankFusionService(),
                        new CitationService(new RagProperties()));
        EvaluationRunnerService runner = new EvaluationRunnerService(
                new EvaluationDatasetLoader(objectMapper),
                executor,
                mock(ExternalEvaluationCaseExecutor.class),
                new EvaluationMetricCalculator(),
                new EvaluationThresholdGate(objectMapper),
                new EvaluationReportWriter(
                        objectMapper, reportDirectory.toString()),
                "mock-fixed-v1");

        var report = runner.run("deterministic");

        report.failures().forEach(System.out::println);
        assertThat(report.totalCount()).isEqualTo(240);
        assertThat(report.passedCount()).isEqualTo(240);
        assertThat(report.failures()).isEmpty();
        assertThat(report.metrics().get("overallPassRate"))
                .isEqualTo(1.0);
        assertThat(report.thresholdGateActive()).isFalse();
        System.out.printf(
                "evaluation cases=%d passed=%d overall=%.4f rag=%.4f tool=%.4f security=%.4f noAnswer=%.4f failures=%d%n",
                report.totalCount(),
                report.passedCount(),
                report.metrics().get("overallPassRate"),
                report.metrics().get("ragPassRate"),
                report.metrics().get("toolRouteCoverage"),
                report.metrics().get("authorizationBlockRate"),
                report.metrics().get("noAnswerAccuracy"),
                report.failedCount());
        try (var files = Files.list(reportDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.endsWith(".json"))
                    .anyMatch(name -> name.endsWith(".md"));
        }
    }

    @Test
    void externalEvaluationShouldStopWhenModelPreflightFails() {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        ExternalEvaluationCaseExecutor external =
                mock(ExternalEvaluationCaseExecutor.class);
        doThrow(new IllegalStateException("model unavailable"))
                .when(external)
                .verifyDependencies();
        EvaluationRunnerService runner = new EvaluationRunnerService(
                new EvaluationDatasetLoader(objectMapper),
                mock(DeterministicEvaluationCaseExecutor.class),
                external,
                new EvaluationMetricCalculator(),
                new EvaluationThresholdGate(objectMapper),
                new EvaluationReportWriter(
                        objectMapper,
                        reportDirectory.toString()),
                "qwen-plus");

        assertThatThrownBy(() -> runner.run("external"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("model unavailable");
        verify(external, never()).execute(
                org.mockito.ArgumentMatchers.any());
    }

}
