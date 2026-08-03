package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CandidateQualityGate {

    public Decision evaluate(
            String goal,
            List<CourseAiClient.CourseSummary> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new Decision(false, "NO_VERIFIED_COURSES");
        }
        boolean hasUsableTitle = candidates.stream()
                .anyMatch(course -> course != null
                        && course.id() != null
                        && course.title() != null
                        && !course.title().isBlank());
        return hasUsableTitle
                ? new Decision(true, "CANDIDATES_AVAILABLE")
                : new Decision(false, "CANDIDATES_INCOMPLETE");
    }

    public record Decision(boolean sufficient, String reason) {
    }
}
