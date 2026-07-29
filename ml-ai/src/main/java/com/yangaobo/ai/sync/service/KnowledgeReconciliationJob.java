package com.yangaobo.ai.sync.service;

import com.yangaobo.ai.knowledge.service.KnowledgeIndexService;
import com.yangaobo.ai.observability.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeReconciliationJob {

    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeReconciliationJob.class);

    private final KnowledgeIndexService knowledgeIndexService;
    private final AiMetrics aiMetrics;

    public KnowledgeReconciliationJob(
            KnowledgeIndexService knowledgeIndexService,
            AiMetrics aiMetrics) {
        this.knowledgeIndexService = knowledgeIndexService;
        this.aiMetrics = aiMetrics;
    }

    @Scheduled(cron = "${ai.knowledge-sync.reconciliation-cron:0 0 3 * * *}")
    public void reconcile() {
        var status = knowledgeIndexService.status();
        long sourceCount = status.sourceCounts().values().stream()
                .mapToLong(Long::longValue).sum();
        long vectorCount = status.vectorCounts().values().stream()
                .mapToLong(Long::longValue).sum();
        aiMetrics.reconciliationDiff(Math.abs(sourceCount - vectorCount));
        if (!knowledgeIndexService.startRebuild()) {
            log.info("Knowledge reconciliation skipped because a rebuild is running");
        }
    }
}
