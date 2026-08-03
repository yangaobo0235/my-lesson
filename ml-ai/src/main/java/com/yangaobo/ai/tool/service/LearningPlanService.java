package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.tool.dto.LearningPlanProgressRequest;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanAdjustment;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LearningPlanService {

    private final LearningPlanRepository repository;

    public LearningPlanService(LearningPlanRepository repository) {
        this.repository = repository;
    }

    public LearningPlan getCurrent() {
        Long userId = UserContext.requireUser().id();
        return repository.findLatestActive(userId)
                .orElseThrow(() -> new BusinessOperationException(
                        "LEARNING_PLAN_NOT_FOUND",
                "当前还没有学习计划，可以先告诉我你的学习目标"));
    }

    public LearningPlan updateProgress(
            java.util.UUID planId,
            LearningPlanProgressRequest request) {
        Long userId = UserContext.requireUser().id();
        String note = request.note() == null || request.note().isBlank()
                ? null
                : request.note().trim();
        return repository.updateProgress(
                        planId,
                        userId,
                        request.progressPercent(),
                        note,
                        adjustments(request.progressPercent()))
                .orElseThrow(() -> new BusinessOperationException(
                        "LEARNING_PLAN_NOT_FOUND",
                        "学习计划不存在或不属于当前用户"));
    }

    private List<LearningPlanAdjustment> adjustments(int progressPercent) {
        if (progressPercent >= 100) {
            return List.of(new LearningPlanAdjustment(
                    "COMPLETED",
                    "计划已完成，可以整理项目作品或复盘课程笔记。"));
        }
        if (progressPercent >= 70) {
            return List.of(new LearningPlanAdjustment(
                    "SPRINT",
                    "进度良好，建议把剩余课程集中到高优先级知识点并安排一次综合复盘。"));
        }
        if (progressPercent >= 30) {
            return List.of(new LearningPlanAdjustment(
                    "KEEP_PACE",
                    "进度稳定，建议继续按每日任务推进，并每周补一次错题或实践记录。"));
        }
        return List.of(new LearningPlanAdjustment(
                "CATCH_UP",
                "当前进度偏早期，建议先缩小范围，优先完成第一门课程和配套练习。"));
    }
}
