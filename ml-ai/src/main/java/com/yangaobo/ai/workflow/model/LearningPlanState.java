package com.yangaobo.ai.workflow.model;

import com.yangaobo.ai.client.CourseAiClient;

import java.util.List;

public record LearningPlanState(
        Long userId,
        String goal,
        int minutesPerDay,
        List<CourseAiClient.CourseSummary> candidates,
        LearningPlanDraft draft,
        List<String> validationErrors,
        String status
) {

    public LearningPlanState {
        candidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
        validationErrors = validationErrors == null
                ? List.of()
                : List.copyOf(validationErrors);
    }
}
