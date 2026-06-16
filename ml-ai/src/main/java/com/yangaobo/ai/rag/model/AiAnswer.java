package com.yangaobo.ai.rag.model;

import java.util.List;

public record AiAnswer(
        String answer,
        List<Citation> citations,
        String traceId
) {
}
