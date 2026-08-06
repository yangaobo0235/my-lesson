package com.yangaobo.search.dto;

public record KnowledgeIndexResponse(
        String sourceType,
        String sourceId,
        long contentVersion,
        String status,
        int indexedChunks,
        long deletedChunks) {
}
