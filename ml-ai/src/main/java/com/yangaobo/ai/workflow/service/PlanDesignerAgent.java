package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanReview;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlanDesignerAgent {

    private final LearningPlanDraftGenerator generator;

    public PlanDesignerAgent(LearningPlanDraftGenerator generator) {
        this.generator = generator;
    }

    public LearningPlanDraft design(
            String goal,
            int minutesPerDay,
            UserAiClient.UserProfile profile,
            List<CourseAiClient.CourseSummary> candidates) {
        return generator.generate(goal, minutesPerDay, profile, candidates);
    }

    public LearningPlanDraft repair(
            String goal,
            int minutesPerDay,
            UserAiClient.UserProfile profile,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft currentDraft,
            List<String> validationErrors,
            LearningPlanReview review) {
        return generator.repair(
                goal,
                minutesPerDay,
                profile,
                candidates,
                currentDraft,
                validationErrors,
                review);
    }

    public LearningPlanDraft deterministicFallback(
            String goal,
            int minutesPerDay,
            List<CourseAiClient.CourseSummary> candidates) {
        return generator.deterministicFallback(
                goal, minutesPerDay, candidates);
    }
}
