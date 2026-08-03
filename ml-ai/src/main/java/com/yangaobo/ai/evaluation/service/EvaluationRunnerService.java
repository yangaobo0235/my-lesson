package com.yangaobo.ai.evaluation.service;

import com.yangaobo.ai.evaluation.model.EvaluationCaseResult;
import com.yangaobo.ai.evaluation.model.EvaluationReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EvaluationRunnerService {

    private final EvaluationDatasetLoader datasetLoader;
    private final DeterministicEvaluationCaseExecutor deterministicExecutor;
    private final ExternalEvaluationCaseExecutor externalExecutor;
    private final EvaluationMetricCalculator metricCalculator;
    private final EvaluationThresholdGate thresholdGate;
    private final EvaluationReportWriter reportWriter;
    private final String modelVersion;
    private final Map<UUID, EvaluationReport> reports =
            new ConcurrentHashMap<>();

    public EvaluationRunnerService(
            EvaluationDatasetLoader datasetLoader,
            DeterministicEvaluationCaseExecutor deterministicExecutor,
            ExternalEvaluationCaseExecutor externalExecutor,
            EvaluationMetricCalculator metricCalculator,
            EvaluationThresholdGate thresholdGate,
            EvaluationReportWriter reportWriter,
            @Value("${spring.ai.dashscope.chat.options.model:qwen3-max}")
            String modelVersion) {
        this.datasetLoader = datasetLoader;
        this.deterministicExecutor = deterministicExecutor;
        this.externalExecutor = externalExecutor;
        this.metricCalculator = metricCalculator;
        this.thresholdGate = thresholdGate;
        this.reportWriter = reportWriter;
        this.modelVersion = modelVersion;
    }

    public EvaluationReport run(String requestedMode) {
        String mode = requestedMode == null
                ? "deterministic"
                : requestedMode.toLowerCase(java.util.Locale.ROOT);
        EvaluationCaseExecutor executor = switch (mode) {
            case "deterministic" -> deterministicExecutor;
            case "external" -> externalExecutor;
            default -> throw new IllegalArgumentException(
                    "Evaluation mode must be deterministic or external");
        };
        if ("external".equals(mode)) {
            externalExecutor.verifyDependencies();
        }
        List<EvaluationCaseResult> results = datasetLoader.load().stream()
                .map(executor::execute)
                .toList();
        Map<String, Double> metrics = metricCalculator.calculate(results);
        long p95 = metricCalculator.p95Latency(results);
        EvaluationThresholdGate.GateResult gate =
                thresholdGate.evaluate(metrics, p95);
        int passed = (int) results.stream()
                .filter(EvaluationCaseResult::passed).count();
        EvaluationReport report = new EvaluationReport(
                UUID.randomUUID(),
                mode,
                EvaluationDatasetLoader.DATASET_VERSION,
                "deterministic".equals(mode) ? "mock-fixed-v1" : modelVersion,
                "agent-profile-v1/rag-v1",
                hash(mode + '|' + modelVersion + '|'
                        + EvaluationDatasetLoader.DATASET_VERSION),
                Instant.now(),
                results.size(),
                passed,
                results.size() - passed,
                results.stream().mapToLong(
                        EvaluationCaseResult::tokenUsage).sum(),
                p95,
                gate.active(),
                gate.passed(),
                metrics,
                results.stream().filter(result -> !result.passed()).toList(),
                results);
        reports.put(report.id(), report);
        reportWriter.write(report);
        return report;
    }

    public EvaluationReport find(UUID id) {
        EvaluationReport report = reports.get(id);
        if (report == null) {
            throw new IllegalArgumentException("Evaluation report not found");
        }
        return report;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
