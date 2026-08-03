package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanReview;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LearningPlanReviewService {

    private final PlanReviewerAgent reviewerAgent;

    public LearningPlanReviewService(PlanReviewerAgent reviewerAgent) {
        this.reviewerAgent = reviewerAgent;
    }

    public LearningPlanReview review(
            String goal,
            int minutesPerDay,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft draft,
            List<String> javaErrors) {
        return reviewerAgent.review(
                goal, minutesPerDay, candidates, draft, javaErrors);
    }
}
