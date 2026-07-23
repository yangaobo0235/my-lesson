package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanAdjustment;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanCourse;
import com.yangaobo.ai.tool.model.LearningPlan.LearningPlanRoutine;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanDraftRecord;
import com.yangaobo.ai.workflow.model.LearningPlanState;
import com.yangaobo.ai.workflow.repository.LearningPlanDraftRepository;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
public class LearningPlanWorkflowService {

    private static final int DEFAULT_MINUTES_PER_DAY = 30;
    private static final int MAX_CANDIDATES = 6;
    private static final String WORKFLOW_NAME = "learning_plan_graph";
    private static final String REQUEST = "request";
    private static final String RUN_ID = "runId";
    private static final String USER_ID = "userId";
    private static final String GOAL = "goal";
    private static final String MINUTES_PER_DAY = "minutesPerDay";
    private static final String PROFILE = "profile";
    private static final String CANDIDATES = "candidates";
    private static final String VERIFIED_CANDIDATES =
            "verifiedCandidates";
    private static final String DRAFT = "draft";
    private static final String VALIDATION_ERRORS = "validationErrors";
    private static final String WORKFLOW_STATUS = "workflowStatus";
    private static final String STATE = "state";
    private static final String RECORD = "record";
    private static final String RUN_CONTEXT = "toolRunContext";

    private final AiBusinessGateway businessGateway;
    private final LearningPlanDraftGenerator generator;
    private final LearningPlanValidator validator;
    private final LearningPlanDraftRepository draftRepository;
    private final LearningPlanRepository planRepository;
    private final BaseCheckpointSaver checkpointSaver;
    private final CompiledGraph graph;

    public LearningPlanWorkflowService(
            AiBusinessGateway businessGateway,
            LearningPlanDraftGenerator generator,
            LearningPlanValidator validator,
            LearningPlanDraftRepository draftRepository,
            LearningPlanRepository planRepository,
            BaseCheckpointSaver checkpointSaver) {
        this.businessGateway = businessGateway;
        this.generator = generator;
        this.validator = validator;
        this.draftRepository = draftRepository;
        this.planRepository = planRepository;
        this.checkpointSaver = checkpointSaver;
        try {
            this.graph = buildGraph();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to compile learning plan graph",
                    exception);
        }
    }

    public LearningPlanDraftRecord prepare(
            UUID runId,
            CreateLearningPlanRequest request) {
        return prepare(runId, request, null);
    }

    public LearningPlanDraftRecord prepare(
            UUID runId,
            CreateLearningPlanRequest request,
            ToolRunContext runContext) {
        try {
            RunnableConfig.Builder configBuilder = RunnableConfig.builder()
                    .threadId(runId.toString());
            if (runContext != null) {
                configBuilder.addMetadata(RUN_CONTEXT, runContext);
            }
            OverAllState state = graph.invoke(
                            Map.of(
                                    RUN_ID, runId,
                                    REQUEST, request),
                            configBuilder.build())
                    .orElseThrow(() -> new IllegalStateException(
                            "Learning plan graph returned no state"));
            return state.value(RECORD, LearningPlanDraftRecord.class)
                    .orElseThrow(() -> new IllegalStateException(
                            "Learning plan graph returned no draft"));
        } catch (BusinessOperationException exception) {
            throw exception;
        } catch (CompletionException exception) {
            if (exception.getCause()
                    instanceof BusinessOperationException business) {
                throw business;
            }
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to execute learning plan graph",
                    exception);
        }
    }

    private CompiledGraph buildGraph() throws Exception {
        StateGraph graph = new StateGraph(
                KeyStrategy.builder()
                        .defaultStrategy(KeyStrategy.REPLACE)
                        .build());
        graph.addNode(
                        "normalize_goal",
                        node(
                                "normalize_goal",
                                "规范化学习目标",
                                this::normalizeGoal))
                .addNode(
                        "load_user_profile",
                        node(
                                "load_user_profile",
                                "读取用户画像",
                                this::loadUserProfile))
                .addNode(
                        "search_candidate_courses",
                        node(
                                "search_candidate_courses",
                                "检索候选课程",
                                this::searchCandidateCourses))
                .addNode(
                        "verify_courses",
                        node(
                                "verify_courses",
                                "校验课程可用性",
                                this::verifyCourses))
                .addNode(
                        "generate_draft",
                        node(
                                "generate_draft",
                                "生成学习计划草案",
                                this::generateDraft))
                .addNode(
                        "validate_draft",
                        node(
                                "validate_draft",
                                "校验草案约束",
                                this::validateDraft))
                .addNode(
                        "persist_draft",
                        node(
                                "persist_draft",
                                "保存待确认草案",
                                this::persistDraft))
                .addNode(
                        "request_approval",
                        node(
                                "request_approval",
                                "进入用户确认",
                                this::requestApproval))
                .addEdge(StateGraph.START, "normalize_goal")
                .addEdge("normalize_goal", "load_user_profile")
                .addEdge("load_user_profile", "search_candidate_courses")
                .addEdge("search_candidate_courses", "verify_courses")
                .addEdge("verify_courses", "generate_draft")
                .addEdge("generate_draft", "validate_draft")
                .addEdge("validate_draft", "persist_draft")
                .addEdge("persist_draft", "request_approval")
                .addEdge("request_approval", StateGraph.END);
        SaverConfig saverConfig = SaverConfig.builder()
                .register(checkpointSaver)
                .build();
        return graph.compile(CompileConfig.builder()
                .saverConfig(saverConfig)
                .build());
    }

    private AsyncNodeActionWithConfig node(
            String nodeName,
            String displayName,
            NodeAction action) {
        return AsyncNodeActionWithConfig.node_async((state, config) -> {
            ToolRunContext runContext = runContext(config);
            Instant startedAt = Instant.now();
            publishNodeStarted(runContext, nodeName, displayName);
            try {
                Map<String, Object> update = action.apply(state);
                publishNodeCompleted(
                        runContext,
                        nodeName,
                        displayName,
                        true,
                        startedAt,
                        nodeMetadata(update),
                        "");
                if ("request_approval".equals(nodeName)) {
                    publishWaitingApproval(runContext, update);
                }
                return update;
            } catch (Exception exception) {
                publishNodeCompleted(
                        runContext,
                        nodeName,
                        displayName,
                        false,
                        startedAt,
                        Map.of(),
                        publicMessage(exception));
                throw exception;
            }
        });
    }

    private ToolRunContext runContext(RunnableConfig config) {
        return config.metadata(RUN_CONTEXT)
                .filter(ToolRunContext.class::isInstance)
                .map(ToolRunContext.class::cast)
                .orElse(null);
    }

    private Map<String, Object> normalizeGoal(OverAllState state) {
        CreateLearningPlanRequest request =
                required(state, REQUEST, CreateLearningPlanRequest.class);
        Long userId = UserContext.requireUser().id();
        String goal = request.goal().trim();
        int minutes = request.availableMinutesPerDay() == null
                ? DEFAULT_MINUTES_PER_DAY
                : request.availableMinutesPerDay();
        return Map.of(
                USER_ID, userId,
                GOAL, goal,
                MINUTES_PER_DAY, minutes);
    }

    private Map<String, Object> loadUserProfile(OverAllState state) {
        return Map.of(PROFILE, businessGateway.getMyProfile());
    }

    private Map<String, Object> searchCandidateCourses(
            OverAllState state) {
        String goal = required(state, GOAL, String.class);
        return Map.of(CANDIDATES, findCourses(goal));
    }

    private Map<String, Object> verifyCourses(OverAllState state) {
        return Map.of(
                VERIFIED_CANDIDATES,
                verifyCandidates(courseSummaries(state, CANDIDATES)));
    }

    private Map<String, Object> generateDraft(OverAllState state) {
        String goal = required(state, GOAL, String.class);
        int minutes = required(state, MINUTES_PER_DAY, Integer.class);
        UserAiClient.UserProfile profile =
                required(state, PROFILE, UserAiClient.UserProfile.class);
        List<CourseAiClient.CourseSummary> candidates =
                courseSummaries(state, VERIFIED_CANDIDATES);
        return Map.of(
                DRAFT,
                generator.generate(
                        goal,
                        minutes,
                        profile,
                        candidates));
    }

    private Map<String, Object> validateDraft(OverAllState state) {
        int minutes = required(state, MINUTES_PER_DAY, Integer.class);
        List<CourseAiClient.CourseSummary> candidates =
                courseSummaries(state, VERIFIED_CANDIDATES);
        LearningPlanDraft draft =
                required(state, DRAFT, LearningPlanDraft.class);
        List<String> errors = validator.validate(
                minutes,
                candidates,
                draft);
        return Map.of(
                VALIDATION_ERRORS, errors,
                WORKFLOW_STATUS, errors.isEmpty()
                        ? "PENDING_CONFIRMATION"
                        : "INVALID");
    }

    private Map<String, Object> persistDraft(OverAllState state) {
        UUID runId = required(state, RUN_ID, UUID.class);
        LearningPlanState learningPlanState =
                new LearningPlanState(
                        required(state, USER_ID, Long.class),
                        required(state, GOAL, String.class),
                        required(state, MINUTES_PER_DAY, Integer.class),
                        courseSummaries(state, VERIFIED_CANDIDATES),
                        required(state, DRAFT, LearningPlanDraft.class),
                        validationErrors(state),
                        required(state, WORKFLOW_STATUS, String.class));
        LearningPlanDraftRecord record =
                draftRepository.save(runId, learningPlanState);
        return Map.of(
                STATE, learningPlanState,
                RECORD, record);
    }

    private Map<String, Object> requestApproval(OverAllState state) {
        List<String> errors = validationErrors(state);
        if (!errors.isEmpty()) {
            throw new BusinessOperationException(
                    "LEARNING_PLAN_INVALID",
                    String.join("；", errors));
        }
        LearningPlanDraftRecord record =
                required(state, RECORD, LearningPlanDraftRecord.class);
        return Map.of(
                "approvalReady", true,
                "draftId", record.id().toString(),
                "goal", record.state().goal(),
                "minutesPerDay", record.state().minutesPerDay(),
                "planDays", record.state().draft().planDays());
    }

    private <T> T required(
            OverAllState state,
            String key,
            Class<T> type) {
        return state.value(key, type)
                .orElseThrow(() -> new IllegalStateException(
                        "Learning plan graph state missing " + key));
    }

    @SuppressWarnings("unchecked")
    private List<CourseAiClient.CourseSummary> courseSummaries(
            OverAllState state,
            String key) {
        Object value = state.data().get(key);
        if (value instanceof List<?> list) {
            return (List<CourseAiClient.CourseSummary>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> validationErrors(OverAllState state) {
        Object value = state.data().get(VALIDATION_ERRORS);
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    private Map<String, Object> nodeMetadata(
            Map<String, Object> update) {
        if (update.containsKey(VERIFIED_CANDIDATES)) {
            return Map.of(
                    "courseCount",
                    listSize(update.get(VERIFIED_CANDIDATES)));
        }
        if (update.containsKey(CANDIDATES)) {
            return Map.of(
                    "courseCount",
                    listSize(update.get(CANDIDATES)));
        }
        if (update.containsKey(VALIDATION_ERRORS)) {
            return Map.of(
                    "errorCount",
                    listSize(update.get(VALIDATION_ERRORS)));
        }
        if (update.containsKey(RECORD)) {
            LearningPlanDraftRecord record =
                    (LearningPlanDraftRecord) update.get(RECORD);
            return Map.of(
                    "draftId", record.id().toString(),
                    "status", record.state().status());
        }
        return Map.of();
    }

    private int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private void publishNodeStarted(
            ToolRunContext context,
            String nodeName,
            String displayName) {
        if (context == null) {
            return;
        }
        context.publish(
                ConversationEventType.WORKFLOW_NODE_STARTED,
                Map.of(
                        "workflowName", WORKFLOW_NAME,
                        "nodeName", nodeName,
                        "displayName", displayName));
    }

    private void publishNodeCompleted(
            ToolRunContext context,
            String nodeName,
            String displayName,
            boolean success,
            Instant startedAt,
            Map<String, Object> metadata,
            String errorMessage) {
        if (context == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflowName", WORKFLOW_NAME);
        data.put("nodeName", nodeName);
        data.put("displayName", displayName);
        data.put("success", success);
        data.put(
                "latencyMillis",
                Math.max(
                        0,
                        Duration.between(
                                startedAt,
                                Instant.now()).toMillis()));
        data.putAll(metadata);
        if (errorMessage != null && !errorMessage.isBlank()) {
            data.put("errorMessage", errorMessage);
        }
        context.publish(
                ConversationEventType.WORKFLOW_NODE_COMPLETED,
                data);
    }

    private void publishWaitingApproval(
            ToolRunContext context,
            Map<String, Object> update) {
        if (context == null) {
            return;
        }
        context.publish(
                ConversationEventType.WORKFLOW_WAITING_APPROVAL,
                Map.of(
                        "workflowName", WORKFLOW_NAME,
                        "nodeName", "request_approval",
                        "displayName", "等待用户确认",
                        "draftId", update.get("draftId"),
                        "goal", update.get("goal"),
                        "minutesPerDay", update.get("minutesPerDay"),
                        "planDays", update.get("planDays")));
    }

    private String publicMessage(Exception exception) {
        if (exception instanceof BusinessOperationException business) {
            return business.getMessage();
        }
        return Optional.ofNullable(exception.getMessage())
                .filter(message -> !message.isBlank())
                .orElse(exception.getClass().getSimpleName());
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
}
