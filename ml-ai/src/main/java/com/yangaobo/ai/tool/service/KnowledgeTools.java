package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.knowledge.service.KnowledgeIndexService;
import com.yangaobo.ai.tool.dto.NoArgsRequest;
import com.yangaobo.ai.tool.model.KnowledgeRebuildResult;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeTools {

    private final KnowledgeIndexService knowledgeIndexService;

    public KnowledgeTools(KnowledgeIndexService knowledgeIndexService) {
        this.knowledgeIndexService = knowledgeIndexService;
    }

    public KnowledgeRebuildResult rebuildKnowledgeIndex(
            NoArgsRequest request) {
        boolean started = knowledgeIndexService.startRebuild();
        return new KnowledgeRebuildResult(
                started,
                knowledgeIndexService.status());
    }
}
