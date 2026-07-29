package com.yangaobo.ai.workflow.model;

import com.yangaobo.ai.client.CourseAiClient;

import java.util.List;
import java.util.UUID;

public record LearningPlanState(
        Long userId,
        String goal,
        int minutesPerDay,
        List<CourseAiClient.CourseSummary> candidates,
        LearningPlanDraft draft,
        List<String> validationErrors,
        LearningPlanReview reviewResult,
        int version,
        UUID previousDraftId,
        int searchAttempts,
        int repairAttempts,
        int reviewAttempts,
        int modelCallCount,
        String terminationReason,
        String adjustmentRequest,
        String status
) {

    public LearningPlanState {
        candidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
        validationErrors = validationErrors == null
                ? List.of()
                : List.copyOf(validationErrors);
        version = Math.max(1, version);
        terminationReason = terminationReason == null ? "" : terminationReason;
        adjustmentRequest = adjustmentRequest == null ? "" : adjustmentRequest;
    }

    public LearningPlanState(
            Long userId,
            String goal,
            int minutesPerDay,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft draft,
            List<String> validationErrors,
            String status) {
        this(userId, goal, minutesPerDay, candidates, draft,
                validationErrors, null, 1, null, 0, 0, 0, 0,
                "", "", status);
    }
}
