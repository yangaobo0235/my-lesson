package com.yangaobo.ai.recommendation.model;

import java.util.List;

public record CourseRecommendationResponse(
        String goal,
        String summary,
        List<RecommendedCourse> recommendedCourses,
        String nextAction
) {

    public record RecommendedCourse(
            Long courseId,
            String title,
            String author,
            String category,
            Double price,
            String cover,
            String reason,
            int priority,
            int estimatedHours,
            boolean owned,
            boolean inCart,
            List<RecommendationCitation> citations
    ) {
    }

    public record RecommendationCitation(
            String sourceType,
            String sourceId,
            String title,
            String snippet
    ) {
    }
}
