package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanReview;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LearningPlanRepairService {

    private final PlanDesignerAgent designerAgent;

    public LearningPlanRepairService(PlanDesignerAgent designerAgent) {
        this.designerAgent = designerAgent;
    }

    public LearningPlanDraft repair(
            String goal,
            int minutesPerDay,
            UserAiClient.UserProfile profile,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft currentDraft,
            List<String> errors,
            LearningPlanReview review) {
        return designerAgent.repair(
                goal, minutesPerDay, profile, candidates,
                currentDraft, errors, review);
    }
}
