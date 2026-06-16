package com.yangaobo.ai.knowledge.model;

import java.time.Instant;
import java.util.Map;

public record KnowledgeIndexStatus(
        String state,
        boolean running,
        Instant startedAt,
        Instant finishedAt,
        long indexedSources,
        long skippedSources,
        long failedSources,
        long indexedChunks,
        Map<String, Long> sourceCounts,
        Map<String, Long> vectorCounts,
        String message
) {
}
