package com.yangaobo.ai.approval.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ApprovalTask(
        UUID id,
        UUID runId,
        Long userId,
        UUID requestId,
        String actionType,
        JsonNode actionPayload,
        String reason,
        String status,
        Instant expiresAt,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt,
        JsonNode response,
        String errorCode
) {

    public boolean pending() {
        return "PENDING".equals(status);
    }

    public boolean terminal() {
        return switch (status) {
            case "APPROVED", "REJECTED", "EXPIRED", "FAILED" -> true;
            default -> false;
        };
    }
}
