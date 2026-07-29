package com.yangaobo.ai.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiMetrics {

    private final Counter requestTotal;
    private final Timer requestLatency;
    private final Counter modelCallTotal;
    private final Timer modelLatency;
    private final Counter toolCallTotal;
    private final Counter toolErrorTotal;
    private final Timer retrievalLatency;
    private final Counter retrievalEmptyTotal;
    private final Counter approvalTotal;
    private final Counter tokenUsage;
    private final DistributionSummary knowledgeEventLag;
    private final Counter inboxDuplicateTotal;
    private final Counter staleVersionSkippedTotal;
    private final Counter indexSuccessTotal;
    private final Counter indexFailureTotal;
    private final Counter reconciliationDiffTotal;

    public AiMetrics(MeterRegistry registry) {
        requestTotal = registry.counter("ai_request_total");
        requestLatency = registry.timer("ai_request_latency");
        modelCallTotal = registry.counter("ai_model_call_total");
        modelLatency = registry.timer("ai_model_latency");
        toolCallTotal = registry.counter("ai_tool_call_total");
        toolErrorTotal = registry.counter("ai_tool_error_total");
        retrievalLatency = registry.timer("ai_retrieval_latency");
        retrievalEmptyTotal = registry.counter("ai_retrieval_empty_total");
        approvalTotal = registry.counter("ai_approval_total");
        tokenUsage = registry.counter("ai_token_usage");
        knowledgeEventLag = registry.summary("knowledge_event_lag_ms");
        inboxDuplicateTotal = registry.counter("inbox_duplicate_total");
        staleVersionSkippedTotal = registry.counter(
                "stale_version_skipped_total");
        indexSuccessTotal = registry.counter("index_success_total");
        indexFailureTotal = registry.counter("index_failure_total");
        reconciliationDiffTotal = registry.counter(
                "reconciliation_diff_total");
    }

    public void request(Duration latency) {
        requestTotal.increment();
        requestLatency.record(latency);
    }

    public void modelCall(Duration latency) {
        modelCallTotal.increment();
        modelLatency.record(latency);
    }

    public void toolCall(boolean success) {
        toolCallTotal.increment();
        if (!success) {
            toolErrorTotal.increment();
        }
    }

    public void retrieval(Duration latency, boolean empty) {
        retrievalLatency.record(latency);
        if (empty) {
            retrievalEmptyTotal.increment();
        }
    }

    public void approval() {
        approvalTotal.increment();
    }

    public void tokens(long total) {
        if (total > 0) {
            tokenUsage.increment(total);
        }
    }

    public void knowledgeEventLag(long millis) {
        knowledgeEventLag.record(Math.max(0L, millis));
    }

    public void inboxDuplicate() {
        inboxDuplicateTotal.increment();
    }

    public void staleVersionSkipped() {
        staleVersionSkippedTotal.increment();
    }

    public void indexSuccess() {
        indexSuccessTotal.increment();
    }

    public void indexFailure() {
        indexFailureTotal.increment();
    }

    public void reconciliationDiff(long count) {
        if (count > 0) {
            reconciliationDiffTotal.increment(count);
        }
    }
}
