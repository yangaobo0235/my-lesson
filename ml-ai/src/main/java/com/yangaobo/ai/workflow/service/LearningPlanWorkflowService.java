package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.agent.config.AgentProperties;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.AuthenticatedUser;
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
import com.yangaobo.ai.workflow.model.LearningPlanReview;
import com.yangaobo.ai.workflow.model.LearningPlanAdjustmentRequest;
import com.yangaobo.ai.workflow.repository.LearningPlanDraftRepository;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LearningPlanWorkflowService {

    private static final int DEFAULT_MINUTES_PER_DAY = 30;
    private static final int MAX_CANDIDATES = 6;
    private static final int MAX_SEARCH_ATTEMPTS = 1;
    private static final int MAX_REPAIR_ATTEMPTS = 2;
    private static final int MAX_REVIEW_ATTEMPTS = 2;
    private static final String WORKFLOW_NAME = "learning_plan_graph";
    private static final String REQUEST = "request";
    private static final String RUN_ID = "runId";
    private static final String USER_ID = "userId";
    private static final String GOAL = "goal";
    private static final String MINUTES_PER_DAY = "minutesPerDay";
    private static final String PROFILE = "profile";
    private static final String CANDIDATES = "candidates";
    private static final String SEARCH_QUERY = "searchQuery";
    private static final String SEARCH_ATTEMPTS = "searchAttempts";
    private static final String VERIFIED_CANDIDATES =
            "verifiedCandidates";
    private static final String DRAFT = "draft";
    private static final String VALIDATION_ERRORS = "validationErrors";
    private static final String CANDIDATE_SUFFICIENT = "candidateSufficient";
    private static final String CANDIDATE_GATE_REASON = "candidateGateReason";
    private static final String REVIEW_RESULT = "reviewResult";
    private static final String REPAIR_ATTEMPTS = "repairAttempts";
    private static final String REVIEW_ATTEMPTS = "reviewAttempts";
    private static final String MODEL_CALL_COUNT = "modelCallCount";
    private static final String TERMINATION_REASON = "terminationReason";
    private static final String VERSION = "version";
    private static final String PREVIOUS_DRAFT_ID = "previousDraftId";
    private static final String ADJUSTMENT_REQUEST = "adjustmentRequest";
    private static final String RECORD = "record";
    private static final String RUN_CONTEXT = "toolRunContext";
    private static final String AUTHENTICATED_USER = "authenticatedUser";
    private static final Pattern REQUESTED_DAYS = Pattern.compile(
            "(\\d{1,2})\\s*(?:天|日)");
    private static final Pattern REQUESTED_WEEKS = Pattern.compile(
            "(\\d{1,2})\\s*(?:周|个?星期)");

    private final AiBusinessGateway businessGateway;
    private final PlanDesignerAgent designerAgent;
    private final CandidateQualityGate candidateQualityGate;
    private final LearningPlanQueryRewriter queryRewriter;
    private final LearningPlanReviewService reviewService;
    private final LearningPlanRepairService repairService;
    private final AgentProperties agentProperties;
    private final LearningPlanValidator validator;
    private final LearningPlanDraftRepository draftRepository;
    private final LearningPlanRepository planRepository;
    private final BaseCheckpointSaver checkpointSaver;
    private final CompiledGraph graph;

    @Autowired
    public LearningPlanWorkflowService(
            AiBusinessGateway businessGateway,
            LearningPlanDraftGenerator generator,
            PlanDesignerAgent designerAgent,
            CandidateQualityGate candidateQualityGate,
            LearningPlanQueryRewriter queryRewriter,
            LearningPlanReviewService reviewService,
            LearningPlanRepairService repairService,
            AgentProperties agentProperties,
            LearningPlanValidator validator,
            LearningPlanDraftRepository draftRepository,
            LearningPlanRepository planRepository,
            BaseCheckpointSaver checkpointSaver) {
        this.businessGateway = businessGateway;
        this.designerAgent = designerAgent;
        this.candidateQualityGate = candidateQualityGate;
        this.queryRewriter = queryRewriter;
        this.reviewService = reviewService;
        this.repairService = repairService;
        this.agentProperties = agentProperties;
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

    LearningPlanWorkflowService(
            AiBusinessGateway businessGateway,
            LearningPlanDraftGenerator generator,
            LearningPlanValidator validator,
            LearningPlanDraftRepository draftRepository,
            LearningPlanRepository planRepository,
            BaseCheckpointSaver checkpointSaver) {
        this(
                businessGateway,
                generator,
                new PlanDesignerAgent(generator),
                new CandidateQualityGate(),
                null,
                null,
                new LearningPlanRepairService(new PlanDesignerAgent(generator)),
                new AgentProperties(),
                validator,
                draftRepository,
                planRepository,
                checkpointSaver);
    }

    public LearningPlanDraftRecord prepare(
            UUID runId,
            CreateLearningPlanRequest request) {
        return prepare(runId, request, null);
    }

    @Transactional
    public LearningPlanDraftRecord adjust(
            UUID previousDraftId,
            LearningPlanAdjustmentRequest request) {
        Long userId = UserContext.requireUser().id();
        LearningPlanDraftRecord previous = draftRepository
                .findOwned(previousDraftId, userId)
                .orElseThrow(() -> new BusinessOperationException(
                        "LEARNING_PLAN_DRAFT_NOT_FOUND",
                        "学习计划草案不存在"));
        if (!List.of("WAITING_CONFIRMATION", "WAITING_ADJUSTMENT",
                        "INSUFFICIENT_DATA")
                .contains(previous.state().status())) {
            throw new BusinessOperationException(
                    "LEARNING_PLAN_DRAFT_NOT_ADJUSTABLE",
                    "学习计划草案当前不可调整");
        }
        UUID runId = draftRepository.createAdjustmentRun(
                userId, request.requestId());
        Optional<LearningPlanDraftRecord> replay =
                draftRepository.findByRun(runId);
        if (replay.isPresent()) {
            return replay.get();
        }
        CreateLearningPlanRequest planRequest = new CreateLearningPlanRequest(
                previous.state().goal(),
                previous.state().minutesPerDay());
        LearningPlanDraftRecord adjusted = invoke(
                runId,
                planRequest,
                null,
                previousDraftId,
                previous.state().version() + 1,
                request.adjustment());
        if (!draftRepository.updateStatus(
                previousDraftId,
                userId,
                previous.state().status(),
                "SUPERSEDED")) {
            draftRepository.updateStatus(
                    adjusted.id(),
                    userId,
                    adjusted.state().status(),
                    "CANCELLED");
            throw new BusinessOperationException(
                    "LEARNING_PLAN_DRAFT_CHANGED",
                    "学习计划草案状态已变化");
        }
        draftRepository.completeAdjustmentRun(runId, adjusted.state());
        return adjusted;
    }

    public LearningPlanDraftRecord prepare(
            UUID runId,
            CreateLearningPlanRequest request,
            ToolRunContext runContext) {
        return invoke(runId, request, runContext, null, 1, "");
    }

    private LearningPlanDraftRecord invoke(
            UUID runId,
            CreateLearningPlanRequest request,
            ToolRunContext runContext,
            UUID previousDraftId,
            int version,
            String adjustment) {
        try {
            AuthenticatedUser authenticatedUser = runContext == null
                    ? UserContext.requireUser()
                    : runContext.user();
            RunnableConfig.Builder configBuilder = RunnableConfig.builder()
                    .threadId(runId.toString())
                    .addMetadata(AUTHENTICATED_USER, authenticatedUser);
            if (runContext != null) {
                configBuilder.addMetadata(RUN_CONTEXT, runContext);
            }
            Map<String, Object> input = new LinkedHashMap<>();
            input.put(RUN_ID, runId);
            input.put(USER_ID, authenticatedUser.id());
            input.put(REQUEST, request);
            input.put(VERSION, version);
            if (previousDraftId != null) {
                input.put(PREVIOUS_DRAFT_ID, previousDraftId);
            }
            if (adjustment != null && !adjustment.isBlank()) {
                input.put(ADJUSTMENT_REQUEST, adjustment.trim());
            }
            OverAllState state = graph.invoke(
                            input,
                            configBuilder.build())
                    .orElseThrow(() -> new IllegalStateException(
                            "Learning plan graph returned no state"));
            LearningPlanDraftRecord record = draftRepository.findByRun(runId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Learning plan graph returned no draft"));
            return normalizeRecord(record);
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
                        "candidate_quality_gate",
                        node(
                                "candidate_quality_gate",
                                "检查候选课程质量",
                                this::candidateQualityGate))
                .addNode(
                        "rewrite_search_query",
                        node(
                                "rewrite_search_query",
                                "改写一次检索词",
                                this::rewriteSearchQuery))
                .addNode(
                        "design_draft",
                        node(
                                "design_draft",
                                "Designer 生成草案",
                                this::designDraft))
                .addNode(
                        "validate_hard_rules",
                        node(
                                "validate_hard_rules",
                                "执行 Java 硬规则",
                                this::validateHardRules))
                .addNode(
                        "review_draft",
                        node(
                                "review_draft",
                                "Reviewer 审查草案",
                                this::reviewDraft))
                .addNode(
                        "repair_draft",
                        node(
                                "repair_draft",
                                "Designer 修正草案",
                                this::repairDraft))
                .addNode(
                        "deterministic_fallback",
                        node(
                                "deterministic_fallback",
                                "生成确定性降级结果",
                                this::deterministicFallback))
                .addNode(
                        "persist_versioned_draft",
                        node(
                                "persist_versioned_draft",
                                "保存版本化草案",
                                this::persistDraft))
                .addNode(
                        "request_user_confirmation",
                        node(
                                "request_user_confirmation",
                                "进入用户确认",
                                this::requestConfirmation))
                .addEdge(StateGraph.START, "normalize_goal")
                .addEdge("normalize_goal", "load_user_profile")
                .addEdge("load_user_profile", "search_candidate_courses")
                .addEdge("search_candidate_courses", "verify_courses")
                .addEdge("verify_courses", "candidate_quality_gate")
                .addConditionalEdges(
                        "candidate_quality_gate",
                        AsyncEdgeAction.edge_async(this::candidateRoute),
                        Map.of(
                                "design", "design_draft",
                                "rewrite", "rewrite_search_query",
                                "fallback", "deterministic_fallback"))
                .addEdge("rewrite_search_query", "search_candidate_courses")
                .addEdge("design_draft", "validate_hard_rules")
                .addConditionalEdges(
                        "validate_hard_rules",
                        AsyncEdgeAction.edge_async(this::hardRuleRoute),
                        Map.of(
                                "review", "review_draft",
                                "repair", "repair_draft",
                                "fallback", "deterministic_fallback"))
                .addEdge("repair_draft", "validate_hard_rules")
                .addConditionalEdges(
                        "review_draft",
                        AsyncEdgeAction.edge_async(this::reviewRoute),
                        Map.of(
                                "persist", "persist_versioned_draft",
                                "repair", "repair_draft",
                                "fallback", "deterministic_fallback"))
                .addEdge("deterministic_fallback", "persist_versioned_draft")
                .addEdge("persist_versioned_draft", "request_user_confirmation")
                .addEdge("request_user_confirmation", StateGraph.END);
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
            AuthenticatedUser previousUser = UserContext.get();
            UserContext.set(authenticatedUser(config));
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
                if ("request_user_confirmation".equals(nodeName)) {
                    publishWaitingConfirmation(runContext, update);
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
            } finally {
                if (previousUser == null) {
                    UserContext.clear();
                } else {
                    UserContext.set(previousUser);
                }
            }
        });
    }

    private ToolRunContext runContext(RunnableConfig config) {
        return config.metadata(RUN_CONTEXT)
                .filter(ToolRunContext.class::isInstance)
                .map(ToolRunContext.class::cast)
                .orElse(null);
    }

    private AuthenticatedUser authenticatedUser(RunnableConfig config) {
        return config.metadata(AUTHENTICATED_USER)
                .filter(AuthenticatedUser.class::isInstance)
                .map(AuthenticatedUser.class::cast)
                .orElseThrow(() -> new IllegalStateException(
                        "Learning plan graph missing authenticated user"));
    }

    private Map<String, Object> normalizeGoal(OverAllState state) {
        CreateLearningPlanRequest request =
                required(state, REQUEST, CreateLearningPlanRequest.class);
        Long userId = required(state, USER_ID, Long.class);
        String goal = request.goal().trim();
        int minutes = request.availableMinutesPerDay() == null
                ? DEFAULT_MINUTES_PER_DAY
                : request.availableMinutesPerDay();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(USER_ID, userId);
        update.put(GOAL, goal);
        String adjustment = state.value(ADJUSTMENT_REQUEST, String.class)
                .orElse("");
        update.put(SEARCH_QUERY, adjustment.isBlank()
                ? goal
                : goal + " " + adjustment);
        update.put(MINUTES_PER_DAY, minutes);
        update.put(SEARCH_ATTEMPTS, integer(state, SEARCH_ATTEMPTS, 0));
        update.put(REPAIR_ATTEMPTS, integer(state, REPAIR_ATTEMPTS, 0));
        update.put(REVIEW_ATTEMPTS, integer(state, REVIEW_ATTEMPTS, 0));
        update.put(MODEL_CALL_COUNT, integer(state, MODEL_CALL_COUNT, 0));
        update.put(VERSION, integer(state, VERSION, 1));
        update.put(TERMINATION_REASON, "");
        return update;
    }

    private Map<String, Object> loadUserProfile(OverAllState state) {
        return Map.of(PROFILE, businessGateway.getMyProfile());
    }

    private Map<String, Object> searchCandidateCourses(
            OverAllState state) {
        String query = state.value(SEARCH_QUERY, String.class)
                .orElseGet(() -> required(state, GOAL, String.class));
        return Map.of(CANDIDATES, findCourses(query));
    }

    private String designGoal(OverAllState state) {
        String goal = required(state, GOAL, String.class);
        String adjustment = state.value(ADJUSTMENT_REQUEST, String.class)
                .orElse("");
        return adjustment.isBlank()
                ? goal
                : goal + "；用户调整：" + adjustment;
    }

    private Map<String, Object> verifyCourses(OverAllState state) {
        return Map.of(
                VERIFIED_CANDIDATES,
                verifyCandidates(courseSummaries(state, CANDIDATES)));
    }

    private Map<String, Object> candidateQualityGate(OverAllState state) {
        CandidateQualityGate.Decision decision = candidateQualityGate.evaluate(
                required(state, GOAL, String.class),
                courseSummaries(state, VERIFIED_CANDIDATES));
        return Map.of(
                CANDIDATE_SUFFICIENT, decision.sufficient(),
                CANDIDATE_GATE_REASON, decision.reason());
    }

    private String candidateRoute(OverAllState state) {
        boolean sufficient = state.value(CANDIDATE_SUFFICIENT, Boolean.class)
                .orElse(false);
        if (sufficient) {
            return canCallModel(state) ? "design" : "fallback";
        }
        return integer(state, SEARCH_ATTEMPTS, 0) < MAX_SEARCH_ATTEMPTS
                && queryRewriter != null
                && canCallModel(state)
                ? "rewrite"
                : "fallback";
    }

    private Map<String, Object> rewriteSearchQuery(OverAllState state) {
        String rewritten = queryRewriter.rewrite(
                required(state, GOAL, String.class),
                required(state, PROFILE, UserAiClient.UserProfile.class));
        return Map.of(
                SEARCH_QUERY, rewritten,
                SEARCH_ATTEMPTS,
                integer(state, SEARCH_ATTEMPTS, 0) + 1,
                MODEL_CALL_COUNT,
                integer(state, MODEL_CALL_COUNT, 0) + 1);
    }

    private Map<String, Object> designDraft(OverAllState state) {
        String goal = designGoal(state);
        int minutes = required(state, MINUTES_PER_DAY, Integer.class);
        UserAiClient.UserProfile profile =
                required(state, PROFILE, UserAiClient.UserProfile.class);
        List<CourseAiClient.CourseSummary> candidates =
                courseSummaries(state, VERIFIED_CANDIDATES);
        LearningPlanDraft draft = enforceRequestedPlanDays(
                goal,
                normalizeDraft(designerAgent.design(
                        goal,
                        minutes,
                        profile,
                        candidates)));
        return Map.of(
                DRAFT,
                draftState(draft),
                MODEL_CALL_COUNT,
                integer(state, MODEL_CALL_COUNT, 0) + 1);
    }

    private Map<String, Object> validateHardRules(OverAllState state) {
        int minutes = required(state, MINUTES_PER_DAY, Integer.class);
        List<CourseAiClient.CourseSummary> candidates =
                courseSummaries(state, VERIFIED_CANDIDATES);
        LearningPlanDraft draft = learningPlanDraft(state);
        List<String> errors = validator.validate(
                minutes,
                candidates,
                draft);
        return Map.of(
                DRAFT, draftState(draft),
                VALIDATION_ERRORS, errors);
    }

    private String hardRuleRoute(OverAllState state) {
        if (validationErrors(state).isEmpty()) {
            return integer(state, REVIEW_ATTEMPTS, 0) < MAX_REVIEW_ATTEMPTS
                    && canCallModel(state)
                    ? "review"
                    : "fallback";
        }
        return integer(state, REPAIR_ATTEMPTS, 0) < MAX_REPAIR_ATTEMPTS
                && canCallModel(state)
                ? "repair"
                : "fallback";
    }

    private Map<String, Object> reviewDraft(OverAllState state) {
        LearningPlanDraft draft = learningPlanDraft(state);
        LearningPlanReview review = normalizeReview(reviewService == null
                ? new LearningPlanReview(true, List.of(), "")
                : reviewService.review(
                        designGoal(state),
                        required(state, MINUTES_PER_DAY, Integer.class),
                        courseSummaries(state, VERIFIED_CANDIDATES),
                        draft,
                        validationErrors(state)));
        return Map.of(
                DRAFT, draftState(draft),
                REVIEW_RESULT, reviewState(review),
                REVIEW_ATTEMPTS,
                integer(state, REVIEW_ATTEMPTS, 0) + 1,
                MODEL_CALL_COUNT,
                integer(state, MODEL_CALL_COUNT, 0) + 1);
    }

    private String reviewRoute(OverAllState state) {
        LearningPlanReview review = learningPlanReview(state)
                .orElse(new LearningPlanReview(false, List.of(), ""));
        if (review.accepted()) {
            return "persist";
        }
        return integer(state, REPAIR_ATTEMPTS, 0) < MAX_REPAIR_ATTEMPTS
                && integer(state, REVIEW_ATTEMPTS, 0) < MAX_REVIEW_ATTEMPTS
                && canCallModel(state)
                ? "repair"
                : "fallback";
    }

    private Map<String, Object> repairDraft(OverAllState state) {
        LearningPlanReview review = learningPlanReview(state).orElse(null);
        String goal = designGoal(state);
        LearningPlanDraft repaired = enforceRequestedPlanDays(
                goal,
                normalizeDraft(repairService.repair(
                goal,
                required(state, MINUTES_PER_DAY, Integer.class),
                required(state, PROFILE, UserAiClient.UserProfile.class),
                courseSummaries(state, VERIFIED_CANDIDATES),
                learningPlanDraft(state),
                validationErrors(state),
                review)));
        return Map.of(
                DRAFT, draftState(repaired),
                REPAIR_ATTEMPTS,
                integer(state, REPAIR_ATTEMPTS, 0) + 1,
                MODEL_CALL_COUNT,
                integer(state, MODEL_CALL_COUNT, 0) + 1);
    }

    private Map<String, Object> deterministicFallback(OverAllState state) {
        String goal = required(state, GOAL, String.class);
        int minutes = required(state, MINUTES_PER_DAY, Integer.class);
        List<CourseAiClient.CourseSummary> candidates =
                courseSummaries(state, VERIFIED_CANDIDATES);
        LearningPlanDraft fallback = enforceRequestedPlanDays(
                goal,
                normalizeDraft(designerAgent.deterministicFallback(
                        goal, minutes, candidates)));
        List<String> errors = candidates.isEmpty()
                ? List.of("没有找到可验证且有权限访问的候选课程")
                : validator.validate(minutes, candidates, fallback);
        String reason = candidates.isEmpty()
                ? "INSUFFICIENT_DATA"
                : errors.isEmpty()
                        ? "DETERMINISTIC_FALLBACK"
                        : "REPAIR_LIMIT_REACHED";
        return Map.of(
                DRAFT, draftState(fallback),
                VALIDATION_ERRORS, errors,
                TERMINATION_REASON, reason);
    }

    private Map<String, Object> persistDraft(OverAllState state) {
        UUID runId = required(state, RUN_ID, UUID.class);
        List<String> errors = validationErrors(state);
        String terminationReason = state.value(
                        TERMINATION_REASON, String.class)
                .orElse("");
        String status = errors.isEmpty()
                ? "WAITING_CONFIRMATION"
                : "INSUFFICIENT_DATA".equals(terminationReason)
                        ? "INSUFFICIENT_DATA"
                        : "WAITING_ADJUSTMENT";
        LearningPlanDraft draft = learningPlanDraft(state);
        LearningPlanReview review = learningPlanReview(state).orElse(null);
        LearningPlanState learningPlanState =
                new LearningPlanState(
                        required(state, USER_ID, Long.class),
                        required(state, GOAL, String.class),
                        required(state, MINUTES_PER_DAY, Integer.class),
                        courseSummaries(state, VERIFIED_CANDIDATES),
                        draft,
                        errors,
                        review,
                        integer(state, VERSION, 1),
                        state.value(PREVIOUS_DRAFT_ID, UUID.class)
                                .orElse(null),
                        integer(state, SEARCH_ATTEMPTS, 0),
                        integer(state, REPAIR_ATTEMPTS, 0),
                        integer(state, REVIEW_ATTEMPTS, 0),
                        integer(state, MODEL_CALL_COUNT, 0),
                        terminationReason,
                        state.value(ADJUSTMENT_REQUEST, String.class)
                                .orElse(""),
                        status);
        LearningPlanDraftRecord record =
                draftRepository.save(runId, learningPlanState);
        return Map.of(
                DRAFT, draftState(draft),
                "draftId", record.id().toString());
    }

    private Map<String, Object> requestConfirmation(OverAllState state) {
        UUID runId = required(state, RUN_ID, UUID.class);
        LearningPlanDraftRecord record = draftRepository.findByRun(runId)
                .map(this::normalizeRecord)
                .orElseThrow(() -> new IllegalStateException(
                        "Learning plan graph persisted no draft"));
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(DRAFT, draftState(record.state().draft()));
        update.put(
                "confirmationReady",
                "WAITING_CONFIRMATION".equals(record.state().status()));
        update.put("draftId", record.id().toString());
        update.put("goal", record.state().goal());
        update.put("minutesPerDay", record.state().minutesPerDay());
        update.put("planDays", record.state().draft().planDays());
        update.put("status", record.state().status());
        update.put(
                "terminationReason",
                record.state().terminationReason());
        return update;
    }

    private <T> T required(
            OverAllState state,
            String key,
            Class<T> type) {
        return state.value(key, type)
                .orElseThrow(() -> new IllegalStateException(
                        "Learning plan graph state missing " + key));
    }

    private List<CourseAiClient.CourseSummary> courseSummaries(
            OverAllState state,
            String key) {
        return normalizeCourseSummaries(state.data().get(key));
    }

    private List<CourseAiClient.CourseSummary> normalizeCourseSummaries(
            Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<CourseAiClient.CourseSummary> courses = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof CourseAiClient.CourseSummary course) {
                courses.add(course);
            } else if (item instanceof Map<?, ?> map) {
                courses.add(new CourseAiClient.CourseSummary(
                        number(map.get("id")).longValue(),
                        text(map.get("title")),
                        text(map.get("author")),
                        text(map.get("category")),
                        decimal(map.get("price")),
                        text(map.get("cover")),
                        dateTime(map.get("updated"))));
            }
        }
        return courses;
    }

    @SuppressWarnings("unchecked")
    private List<String> validationErrors(OverAllState state) {
        Object value = state.data().get(VALIDATION_ERRORS);
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    private int integer(
            OverAllState state,
            String key,
            int defaultValue) {
        return state.value(key, Integer.class).orElse(defaultValue);
    }

    private LearningPlanDraft learningPlanDraft(OverAllState state) {
        Object value = state.data().get(DRAFT);
        if (value instanceof LearningPlanDraft draft) {
            return normalizeDraft(draft);
        }
        if (value instanceof Map<?, ?> map) {
            return new LearningPlanDraft(
                    number(map.get("planDays")).intValue(),
                    draftCourses(map.get("courses")),
                    draftRoutines(map.get("dailyRoutine")),
                    text(map.get("summary")));
        }
        throw new IllegalStateException(
                "Learning plan graph state missing " + DRAFT);
    }

    private Map<String, Object> draftState(LearningPlanDraft draft) {
        List<Map<String, Object>> courses = draft.courses().stream()
                .map(course -> Map.<String, Object>of(
                        "order", course.order(),
                        "courseId", course.courseId(),
                        "objective", text(course.objective())))
                .toList();
        List<Map<String, Object>> routines = draft.dailyRoutine().stream()
                .map(routine -> Map.<String, Object>of(
                        "activity", text(routine.activity()),
                        "minutes", routine.minutes()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planDays", draft.planDays());
        result.put("courses", courses);
        result.put("dailyRoutine", routines);
        result.put("summary", text(draft.summary()));
        return result;
    }

    private List<LearningPlanDraft.DraftCourse> draftCourses(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<LearningPlanDraft.DraftCourse> courses = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof LearningPlanDraft.DraftCourse course) {
                courses.add(course);
            } else if (item instanceof Map<?, ?> map) {
                courses.add(new LearningPlanDraft.DraftCourse(
                        number(map.get("order")).intValue(),
                        number(map.get("courseId")).longValue(),
                        text(map.get("objective"))));
            }
        }
        return courses;
    }

    private List<LearningPlanDraft.DraftRoutine> draftRoutines(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<LearningPlanDraft.DraftRoutine> routines = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof LearningPlanDraft.DraftRoutine routine) {
                routines.add(routine);
            } else if (item instanceof Map<?, ?> map) {
                routines.add(new LearningPlanDraft.DraftRoutine(
                        text(map.get("activity")),
                        number(map.get("minutes")).intValue()));
            }
        }
        return routines;
    }

    private LearningPlanDraft normalizeDraft(LearningPlanDraft raw) {
        List<LearningPlanDraft.DraftCourse> courses = new ArrayList<>();
        for (Object item : (List<?>) raw.courses()) {
            if (item instanceof LearningPlanDraft.DraftCourse course) {
                courses.add(course);
            } else if (item instanceof Map<?, ?> map) {
                courses.add(new LearningPlanDraft.DraftCourse(
                        number(map.get("order")).intValue(),
                        number(map.get("courseId")).longValue(),
                        text(map.get("objective"))));
            }
        }
        List<LearningPlanDraft.DraftRoutine> routines = new ArrayList<>();
        for (Object item : (List<?>) raw.dailyRoutine()) {
            if (item instanceof LearningPlanDraft.DraftRoutine routine) {
                routines.add(routine);
            } else if (item instanceof Map<?, ?> map) {
                routines.add(new LearningPlanDraft.DraftRoutine(
                        text(map.get("activity")),
                        number(map.get("minutes")).intValue()));
            }
        }
        return new LearningPlanDraft(
                raw.planDays(), courses, routines, raw.summary());
    }

    private LearningPlanDraft enforceRequestedPlanDays(
            String goal,
            LearningPlanDraft draft) {
        int requestedDays = requestedPlanDays(goal)
                .orElse(draft.planDays());
        if (requestedDays == draft.planDays()) {
            return draft;
        }
        return new LearningPlanDraft(
                requestedDays,
                draft.courses(),
                draft.dailyRoutine(),
                draft.summary());
    }

    private Optional<Integer> requestedPlanDays(String goal) {
        String text = goal == null ? "" : goal;
        Matcher days = REQUESTED_DAYS.matcher(text);
        if (days.find()) {
            return boundedDays(Integer.parseInt(days.group(1)));
        }
        Matcher weeks = REQUESTED_WEEKS.matcher(text);
        if (weeks.find()) {
            return boundedDays(Integer.parseInt(weeks.group(1)) * 7);
        }
        return Optional.empty();
    }

    private Optional<Integer> boundedDays(int days) {
        return days >= 1 && days <= 90
                ? Optional.of(days)
                : Optional.empty();
    }

    private LearningPlanReview normalizeReview(LearningPlanReview raw) {
        List<LearningPlanReview.Issue> issues = new ArrayList<>();
        for (Object item : (List<?>) raw.issues()) {
            if (item instanceof LearningPlanReview.Issue issue) {
                issues.add(issue);
            } else if (item instanceof Map<?, ?> map) {
                issues.add(new LearningPlanReview.Issue(
                        text(map.get("code")),
                        text(map.get("message")),
                        longList(map.get("courseIds")),
                        integerList(map.get("evidenceRefs"))));
            }
        }
        return new LearningPlanReview(
                raw.accepted(), issues, raw.suggestedFocus());
    }

    private Optional<LearningPlanReview> learningPlanReview(
            OverAllState state) {
        Object value = state.data().get(REVIEW_RESULT);
        if (value instanceof LearningPlanReview review) {
            return Optional.of(normalizeReview(review));
        }
        if (!(value instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        List<LearningPlanReview.Issue> issues = new ArrayList<>();
        Object rawIssues = map.get("issues");
        if (rawIssues instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> issue) {
                    issues.add(new LearningPlanReview.Issue(
                            text(issue.get("code")),
                            text(issue.get("message")),
                            longList(issue.get("courseIds")),
                            integerList(issue.get("evidenceRefs"))));
                }
            }
        }
        return Optional.of(new LearningPlanReview(
                Boolean.TRUE.equals(map.get("accepted")),
                issues,
                text(map.get("suggestedFocus"))));
    }

    private Map<String, Object> reviewState(LearningPlanReview review) {
        List<Map<String, Object>> issues = review.issues().stream()
                .map(issue -> Map.<String, Object>of(
                        "code", text(issue.code()),
                        "message", text(issue.message()),
                        "courseIds", issue.courseIds(),
                        "evidenceRefs", issue.evidenceRefs()))
                .toList();
        return Map.of(
                "accepted", review.accepted(),
                "issues", issues,
                "suggestedFocus", text(review.suggestedFocus()));
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::number)
                .map(Number::longValue)
                .toList();
    }

    private List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::number)
                .map(Number::intValue)
                .toList();
    }

    private LearningPlanDraftRecord normalizeRecord(
            LearningPlanDraftRecord record) {
        LearningPlanState state = record.state();
        LearningPlanState normalized = new LearningPlanState(
                state.userId(),
                state.goal(),
                state.minutesPerDay(),
                normalizeCourseSummaries(state.candidates()),
                normalizeDraft(state.draft()),
                state.validationErrors(),
                state.reviewResult() == null
                        ? null
                        : normalizeReview(state.reviewResult()),
                state.version(),
                state.previousDraftId(),
                state.searchAttempts(),
                state.repairAttempts(),
                state.reviewAttempts(),
                state.modelCallCount(),
                state.terminationReason(),
                state.adjustmentRequest(),
                state.status());
        return new LearningPlanDraftRecord(
                record.id(), record.runId(), normalized,
                record.createdAt(), record.updatedAt());
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Double decimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number
                ? number.doubleValue()
                : Double.valueOf(String.valueOf(value));
    }

    private LocalDateTime dateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof LocalDateTime localDateTime
                ? localDateTime
                : LocalDateTime.parse(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean canCallModel(OverAllState state) {
        return integer(state, MODEL_CALL_COUNT, 0)
                < Math.max(1, agentProperties.getMaxModelCalls());
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
        if (update.containsKey(REVIEW_RESULT)) {
            Object value = update.get(REVIEW_RESULT);
            boolean accepted = value instanceof LearningPlanReview review
                    ? review.accepted()
                    : value instanceof Map<?, ?> map
                            && Boolean.TRUE.equals(map.get("accepted"));
            int issueCount = value instanceof LearningPlanReview review
                    ? review.issues().size()
                    : value instanceof Map<?, ?> map
                            ? listSize(map.get("issues"))
                            : 0;
            return Map.of(
                    "accepted", accepted,
                    "issueCount", issueCount);
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

    private void publishWaitingConfirmation(
            ToolRunContext context,
            Map<String, Object> update) {
        if (context == null) {
            return;
        }
        context.publish(
                ConversationEventType.WORKFLOW_WAITING_CONFIRMATION,
                Map.of(
                        "workflowName", WORKFLOW_NAME,
                        "nodeName", "request_user_confirmation",
                        "displayName", "等待用户确认",
                        "draftId", update.get("draftId"),
                        "goal", update.get("goal"),
                        "minutesPerDay", update.get("minutesPerDay"),
                        "planDays", update.get("planDays"),
                        "status", update.get("status"),
                        "terminationReason", update.get("terminationReason"),
                        "confirmationReady", update.get("confirmationReady")));
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
    public LearningPlan confirm(UUID draftId) {
        Long userId = UserContext.requireUser().id();
        LearningPlanDraftRecord record = draftRepository
                .findOwned(draftId, userId)
                .orElseThrow(() -> new BusinessOperationException(
                        "LEARNING_PLAN_DRAFT_NOT_FOUND",
                        "学习计划草案不存在"));
        String pendingStatus = record.state().status();
        if (!"WAITING_CONFIRMATION".equals(pendingStatus)) {
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
        if (!draftRepository.updateStatus(
                draftId,
                userId,
                pendingStatus,
                "CONFIRMING")) {
            throw new BusinessOperationException(
                    "LEARNING_PLAN_DRAFT_CHANGED",
                    "学习计划草案状态已变化");
        }
        LearningPlan plan = toPlan(record.state(), candidates);
        planRepository.supersedeActive(userId);
        LearningPlan saved = planRepository.insert(userId, plan);
        if (!draftRepository.updateStatus(
                draftId,
                userId,
                "CONFIRMING",
                "CONFIRMED")) {
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
                "WAITING_CONFIRMATION",
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
