package com.yangaobo.ai.sync.service;

import com.yangaobo.ai.knowledge.service.KnowledgeIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeReconciliationJob {

    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeReconciliationJob.class);

    private final KnowledgeIndexService knowledgeIndexService;

    public KnowledgeReconciliationJob(KnowledgeIndexService knowledgeIndexService) {
        this.knowledgeIndexService = knowledgeIndexService;
    }

    @Scheduled(cron = "${ai.knowledge-sync.reconciliation-cron:0 0 3 * * *}")
    public void reconcile() {
        if (!knowledgeIndexService.startRebuild()) {
            log.info("Knowledge reconciliation skipped because a rebuild is running");
        }
    }
}
