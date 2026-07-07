package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanAdjustment;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanCourse;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanRoutine;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanDraftRecord;
import com.yangaobo.ai.workflow.model.LearningPlanState;
import com.yangaobo.ai.workflow.repository.LearningPlanDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LearningPlanWorkflowService {

    private static final int DEFAULT_MINUTES_PER_DAY = 30;
    private static final int MAX_CANDIDATES = 6;

    private final AiBusinessGateway businessGateway;
    private final LearningPlanDraftGenerator generator;
    private final LearningPlanValidator validator;
    private final LearningPlanDraftRepository draftRepository;
    private final LearningPlanRepository planRepository;

    public LearningPlanWorkflowService(
            AiBusinessGateway businessGateway,
            LearningPlanDraftGenerator generator,
            LearningPlanValidator validator,
            LearningPlanDraftRepository draftRepository,
            LearningPlanRepository planRepository) {
        this.businessGateway = businessGateway;
        this.generator = generator;
        this.validator = validator;
        this.draftRepository = draftRepository;
        this.planRepository = planRepository;
    }

    public LearningPlanDraftRecord prepare(
            UUID runId,
            CreateLearningPlanRequest request) {
        Long userId = UserContext.requireUser().id();
        String goal = request.goal().trim();
        int minutes = request.availableMinutesPerDay() == null
                ? DEFAULT_MINUTES_PER_DAY
                : request.availableMinutesPerDay();

        UserAiClient.UserProfile profile =
                businessGateway.getMyProfile();
        List<CourseAiClient.CourseSummary> candidates =
                verifyCandidates(findCourses(goal));
        LearningPlanDraft draft = generator.generate(
                goal,
                minutes,
                profile,
                candidates);
        List<String> errors = validator.validate(
                minutes,
                candidates,
                draft);
        LearningPlanState state = new LearningPlanState(
                userId,
                goal,
                minutes,
                candidates,
                draft,
                errors,
                errors.isEmpty()
                        ? "PENDING_CONFIRMATION"
                        : "INVALID");
        LearningPlanDraftRecord record =
                draftRepository.save(runId, state);
        if (!errors.isEmpty()) {
            throw new BusinessOperationException(
                    "LEARNING_PLAN_INVALID",
                    String.join("；", errors));
        }
        return record;
    }

    @Transactional
    public LearningPlan approve(UUID draftId) {
        Long userId = UserContext.requireUser().id();
        LearningPlanDraftRecord record = draftRepository
                .findOwned(draftId, userId)
                .orElseThrow(() -> new BusinessOperationException(
                        "LEARNING_PLAN_DRAFT_NOT_FOUND",
                        "学习计划草案不存在"));
        if (!"PENDING_CONFIRMATION".equals(record.state().status())) {
            throw new BusinessOperationException(
                    "LEARNING_PLAN_DRAFT_NOT_PENDING",
                    "学习计划草案当前不可确认");
        }

        List<CourseAiClient.CourseSummary> candidates =
                verifyCandidates(record.state().candidates());
        List<String> errors = validator.validate(
                record.state().minutesPerDay(),
                candidates,
                record.state().draft());
        if (!errors.isEmpty()) {
            throw new BusinessOperationException(
                    "LEARNING_PLAN_INVALID",
                    String.join("；", errors));
        }
        LearningPlan plan = toPlan(record.state(), candidates);
        planRepository.supersedeActive(userId);
        LearningPlan saved = planRepository.insert(userId, plan);
        if (!draftRepository.updateStatus(
                draftId,
                userId,
                "PENDING_CONFIRMATION",
                "SAVED")) {
            throw new BusinessOperationException(
                    "LEARNING_PLAN_DRAFT_CHANGED",
                    "学习计划草案状态已变化");
        }
        return saved;
    }

    public void cancel(UUID draftId) {
        Long userId = UserContext.requireUser().id();
        draftRepository.updateStatus(
                draftId,
                userId,
                "PENDING_CONFIRMATION",
                "CANCELLED");
    }

    private LearningPlan toPlan(
            LearningPlanState state,
            List<CourseAiClient.CourseSummary> candidates) {
        Map<Long, CourseAiClient.CourseSummary> byId =
                new LinkedHashMap<>();
        candidates.forEach(course -> byId.put(course.id(), course));
        List<LearningPlanCourse> courses = new ArrayList<>();
        for (LearningPlanDraft.DraftCourse selected
                : state.draft().courses()) {
            CourseAiClient.CourseSummary course =
                    byId.get(selected.courseId());
            courses.add(new LearningPlanCourse(
                    selected.order(),
                    course.id(),
                    course.title(),
                    course.author(),
                    course.category(),
                    selected.objective()));
        }
        List<LearningPlanRoutine> routines =
                state.draft().dailyRoutine().stream()
                        .map(routine -> new LearningPlanRoutine(
                                routine.activity(),
                                routine.minutes()))
                        .toList();
        return new LearningPlan(
                UUID.randomUUID(),
                state.goal(),
                state.minutesPerDay(),
                Math.max(
                        1,
                        (int) Math.ceil(
                                state.draft().planDays() / 7.0)),
                "ACTIVE",
                0,
                null,
                courses,
                routines,
                List.of(new LearningPlanAdjustment(
                        "CATCH_UP",
                        "计划刚开始，建议先完成第一门课程并记录学习笔记。")),
                null,
                null);
    }

    private List<CourseAiClient.CourseSummary> verifyCandidates(
            List<CourseAiClient.CourseSummary> candidates) {
        Map<Long, CourseAiClient.CourseSummary> verified =
                new LinkedHashMap<>();
        for (CourseAiClient.CourseSummary candidate : candidates) {
            if (candidate == null || candidate.id() == null) {
                continue;
            }
            try {
                CourseAiClient.CourseKnowledge current =
                        businessGateway.getCourse(candidate.id());
                if (current != null
                        && candidate.id().equals(current.id())) {
                    verified.putIfAbsent(candidate.id(), candidate);
                }
            } catch (BusinessOperationException exception) {
                // Invalid or removed courses are excluded from the workflow.
            }
        }
        return List.copyOf(verified.values());
    }

    private List<CourseAiClient.CourseSummary> findCourses(String goal) {
        Map<Long, CourseAiClient.CourseSummary> result =
                new LinkedHashMap<>();
        for (String keyword : searchKeywords(goal)) {
            List<CourseAiClient.CourseSummary> courses =
                    businessGateway.searchCourses(keyword, MAX_CANDIDATES);
            for (CourseAiClient.CourseSummary course : courses) {
                result.putIfAbsent(course.id(), course);
                if (result.size() >= MAX_CANDIDATES) {
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
                MAX_CANDIDATES);
    }

    private List<String> searchKeywords(String goal) {
        List<String> keywords = new ArrayList<>();
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
}
