package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanCourse;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanRoutine;
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
                planCourses(courses, goal),
                dailyRoutine(minutes),
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
        List<CourseAiClient.CourseSummary> courses =
                businessGateway.searchCourses(
                        goal,
                        MAX_RECOMMENDED_COURSES);
        if (!courses.isEmpty()) {
            return courses;
        }
        String simplified = goal
                .replaceAll(
                        "学习计划|学习|入门|课程|计划|掌握|提升|基础",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (simplified.isBlank() || simplified.equals(goal)) {
            return courses;
        }
        return businessGateway.searchCourses(
                simplified,
                MAX_RECOMMENDED_COURSES);
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
}
