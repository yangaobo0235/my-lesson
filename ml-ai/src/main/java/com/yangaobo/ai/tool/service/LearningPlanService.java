package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.dto.LearningPlanProgressRequest;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanCourse;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanRoutine;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanAdjustment;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LearningPlanService {

    private static final int DEFAULT_MINUTES_PER_DAY = 30;
    private static final int MAX_RECOMMENDED_COURSES = 6;

    private final AiBusinessGateway businessGateway;
    private final LearningPlanRepository repository;

    public LearningPlanService(
            AiBusinessGateway businessGateway,
            LearningPlanRepository repository) {
        this.businessGateway = businessGateway;
        this.repository = repository;
    }

    @Transactional
    public LearningPlan create(CreateLearningPlanRequest request) {
        Long userId = UserContext.requireUser().id();
        String goal = request.goal().trim();
        int minutes = request.availableMinutesPerDay() == null
                ? DEFAULT_MINUTES_PER_DAY
                : request.availableMinutesPerDay();
        List<CourseAiClient.CourseSummary> courses =
                findCourses(goal);
        LearningPlan draft = new LearningPlan(
                UUID.randomUUID(),
                goal,
                minutes,
                estimatedWeeks(courses.size()),
                "ACTIVE",
                0,
                null,
                planCourses(courses, goal),
                dailyRoutine(minutes),
                adjustments(0),
                null,
                null);
        repository.supersedeActive(userId);
        return repository.insert(userId, draft);
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

    private List<LearningPlanCourse> planCourses(
            List<CourseAiClient.CourseSummary> courses,
            String goal) {
        List<LearningPlanCourse> result = new ArrayList<>();
        for (int index = 0; index < courses.size(); index++) {
            CourseAiClient.CourseSummary course = courses.get(index);
            result.add(new LearningPlanCourse(
                    index + 1,
                    course.id(),
                    course.title(),
                    course.author(),
                    course.category(),
                    "围绕“" + goal + "”完成课程学习并整理实践笔记"));
        }
        return List.copyOf(result);
    }

    private List<CourseAiClient.CourseSummary> findCourses(
            String goal) {
        java.util.Map<Long, CourseAiClient.CourseSummary> result =
                new java.util.LinkedHashMap<>();
        for (String keyword : searchKeywords(goal)) {
            List<CourseAiClient.CourseSummary> courses =
                    businessGateway.searchCourses(
                            keyword,
                            MAX_RECOMMENDED_COURSES);
            for (CourseAiClient.CourseSummary course : courses) {
                result.putIfAbsent(course.id(), course);
                if (result.size() >= MAX_RECOMMENDED_COURSES) {
                    return List.copyOf(result.values());
                }
            }
        }
        if (!result.isEmpty()) {
            return List.copyOf(result.values());
        }
        String simplified = goal
                .replaceAll(
                        "学习计划|学习|入门|课程|计划|掌握|提升|基础",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (simplified.isBlank()) {
            return List.of();
        }
        return businessGateway.searchCourses(
                simplified,
                MAX_RECOMMENDED_COURSES);
    }

    private List<String> searchKeywords(String goal) {
        List<String> keywords = new ArrayList<>();
        if (goal != null && !goal.isBlank()) {
            keywords.add(goal.trim());
        }
        if (containsAny(goal, "表达", "沟通", "公开分享", "汇报")) {
            keywords.add("表达");
        }
        if (containsAny(goal, "临场", "稳定", "自信", "紧张", "放松", "呼吸")) {
            keywords.add("临场");
            keywords.add("放松");
        }
        if (containsAny(goal, "学习", "效率", "时间", "计划")) {
            keywords.add("学习");
        }
        if (containsAny(goal, "睡眠", "睡觉", "失眠", "冥想", "睡前")) {
            keywords.add("睡眠");
        }
        if (containsAny(goal, "运动", "低强度", "跑步", "慢跑", "拉伸")) {
            keywords.add("运动");
        }
        return keywords.stream().distinct().toList();
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<LearningPlanRoutine> dailyRoutine(int totalMinutes) {
        int learning = Math.max(1, totalMinutes * 60 / 100);
        int practice = Math.max(1, totalMinutes * 30 / 100);
        int review = Math.max(1, totalMinutes - learning - practice);
        return List.of(
                new LearningPlanRoutine("学习课程新内容", learning),
                new LearningPlanRoutine("练习并整理笔记", practice),
                new LearningPlanRoutine("复盘当天重点", review));
    }

    private int estimatedWeeks(int courseCount) {
        return Math.max(4, Math.min(12, courseCount * 2));
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
