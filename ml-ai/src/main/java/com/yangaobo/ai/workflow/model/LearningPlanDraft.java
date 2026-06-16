package com.yangaobo.ai.workflow.model;

import java.util.List;

public record LearningPlanDraft(
        int planDays,
        List<DraftCourse> courses,
        List<DraftRoutine> dailyRoutine,
        String summary
) {

    public LearningPlanDraft {
        courses = courses == null ? List.of() : List.copyOf(courses);
        dailyRoutine = dailyRoutine == null
                ? List.of()
                : List.copyOf(dailyRoutine);
    }

    public record DraftCourse(
            int order,
            Long courseId,
            String objective
    ) {
    }

    public record DraftRoutine(
            String activity,
            int minutes
    ) {
    }
}
