package com.yangaobo.ai.workflow.model;

import java.util.List;

public record LearningPlanReview(
        boolean accepted,
        List<Issue> issues,
        String suggestedFocus
) {

    public LearningPlanReview {
        issues = issues == null ? List.of() : List.copyOf(issues);
        suggestedFocus = suggestedFocus == null ? "" : suggestedFocus;
    }

    public record Issue(
            String code,
            String message,
            List<Long> courseIds,
            List<Integer> evidenceRefs
    ) {

        public Issue {
            courseIds = courseIds == null ? List.of() : List.copyOf(courseIds);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }
}
