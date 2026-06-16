package com.yangaobo.ai.tool.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LearningPlan(
        UUID id,
        String goal,
        int availableMinutesPerDay,
        int estimatedWeeks,
        String status,
        List<LearningPlanCourse> courses,
        List<LearningPlanRoutine> dailyRoutine,
        Instant createdAt,
        Instant updatedAt
) {

    public LearningPlan {
        courses = courses == null ? List.of() : List.copyOf(courses);
        dailyRoutine = dailyRoutine == null
                ? List.of()
                : List.copyOf(dailyRoutine);
    }

    public record LearningPlanCourse(
            int order,
            Long courseId,
            String title,
            String author,
            String category,
            String objective
    ) {
    }

    public record LearningPlanRoutine(
            String activity,
            int minutes
    ) {
    }
}
