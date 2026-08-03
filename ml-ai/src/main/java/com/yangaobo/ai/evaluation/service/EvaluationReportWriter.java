package com.yangaobo.ai.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.evaluation.model.EvaluationCaseResult;
import com.yangaobo.ai.evaluation.model.EvaluationReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class EvaluationReportWriter {

    private final ObjectMapper objectMapper;
    private final Path reportDirectory;

    public EvaluationReportWriter(
            ObjectMapper objectMapper,
            @Value("${ai.evaluation.report-dir:target/evaluation}")
            String reportDirectory) {
        this.objectMapper = objectMapper;
        this.reportDirectory = Path.of(reportDirectory).normalize();
    }

    public void write(EvaluationReport report) {
        try {
            Files.createDirectories(reportDirectory);
            String base = "evaluation-" + report.id();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    reportDirectory.resolve(base + ".json").toFile(), report);
            Files.writeString(
                    reportDirectory.resolve(base + ".md"),
                    markdown(report),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to write evaluation report", exception);
        }
    }

    private String markdown(EvaluationReport report) {
        StringBuilder text = new StringBuilder()
                .append("# MyLesson Evaluation Report\n\n")
                .append("- Run: `").append(report.id()).append("`\n")
                .append("- Mode: `").append(report.mode()).append("`\n")
                .append("- Dataset: `").append(report.datasetVersion()).append("`\n")
                .append("- Model: `").append(report.modelVersion()).append("`\n")
                .append("- Prompt: `").append(report.promptVersion()).append("`\n")
                .append("- Config hash: `").append(report.configHash()).append("`\n")
                .append("- Passed: ").append(report.passedCount())
                .append('/').append(report.totalCount()).append("\n")
                .append("- P95 latency: ").append(report.p95LatencyMs())
                .append(" ms\n\n## Metrics\n\n");
        report.metrics().forEach((name, value) -> text
                .append("- ").append(name).append(": ")
                .append(String.format(java.util.Locale.ROOT, "%.4f", value))
                .append('\n'));
        text.append("\n## Failures\n\n");
        if (report.failures().isEmpty()) {
            text.append("None.\n");
        } else {
            for (EvaluationCaseResult failure : report.failures()) {
                text.append("- `").append(failure.caseId()).append("`: ")
                        .append(failure.failureReason()).append('\n');
            }
        }
        return text.toString();
    }
}
