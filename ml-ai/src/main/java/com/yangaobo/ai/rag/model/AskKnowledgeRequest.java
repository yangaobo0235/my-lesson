package com.yangaobo.ai.rag.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskKnowledgeRequest(
        @NotBlank
        @Size(max = 500)
        String question
) {
}
