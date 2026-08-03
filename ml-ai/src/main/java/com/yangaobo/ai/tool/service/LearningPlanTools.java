package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.tool.dto.NoArgsRequest;
import com.yangaobo.ai.tool.model.LearningPlan;
import org.springframework.stereotype.Component;

@Component
public class LearningPlanTools {

    private final LearningPlanService learningPlanService;

    public LearningPlanTools(LearningPlanService learningPlanService) {
        this.learningPlanService = learningPlanService;
    }

    public LearningPlan getLearningPlan(NoArgsRequest request) {
        return learningPlanService.getCurrent();
    }
}
