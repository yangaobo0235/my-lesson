package com.yangaobo.ai.tool.model;

import com.yangaobo.ai.knowledge.model.KnowledgeIndexStatus;

public record KnowledgeRebuildResult(
        boolean started,
        KnowledgeIndexStatus status
) {
}
