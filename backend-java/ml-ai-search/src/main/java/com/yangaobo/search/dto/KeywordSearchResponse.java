package com.yangaobo.search.dto;

import java.util.List;

public record KeywordSearchResponse(
        String index,
        long tookMs,
        List<Hit> hits,
        boolean partial) {

    public record Hit(
            String chunkId,
            int rank,
            double score,
            String sourceType,
            String sourceId,
            long contentVersion) {
    }
}
