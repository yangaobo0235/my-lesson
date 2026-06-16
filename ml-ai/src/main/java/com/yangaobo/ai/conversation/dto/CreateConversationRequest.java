package com.yangaobo.ai.conversation.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 200)
        String title
) {
}
