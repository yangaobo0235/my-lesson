package com.yangaobo.ai.approval.model;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRequestResult(
        UUID approvalId,
        String actionType,
        String status,
        String reason,
        Instant expiresAt
) {
}
