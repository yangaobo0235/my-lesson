package com.yangaobo.ai.approval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.ai.approval.config.ApprovalProperties;
import com.yangaobo.ai.approval.exception.ApprovalNotFoundException;
import com.yangaobo.ai.approval.exception.ApprovalStateException;
import com.yangaobo.ai.approval.model.ApprovalRequestResult;
import com.yangaobo.ai.approval.model.ApprovalTask;
import com.yangaobo.ai.approval.repository.ApprovalTaskRepository;
import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.knowledge.service.KnowledgeAdminGuard;
import com.yangaobo.ai.knowledge.service.KnowledgeIndexService;
import com.yangaobo.ai.observability.AiMetrics;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.CourseIdRequest;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.model.KnowledgeRebuildResult;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.workflow.model.LearningPlanDraftRecord;
import com.yangaobo.ai.workflow.service.LearningPlanWorkflowService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ApprovalService {

    public static final String ADD_CART = "add_course_to_my_cart";
    public static final String REMOVE_CART =
            "remove_course_from_my_cart";
    public static final String CREATE_PLAN = "create_learning_plan";
    public static final String REBUILD_INDEX =
            "rebuild_knowledge_index";

    private final ApprovalTaskRepository repository;
    private final ApprovalProperties properties;
    private final ObjectMapper objectMapper;
    private final AiBusinessGateway businessGateway;
    private final LearningPlanWorkflowService learningPlanWorkflow;
    private final KnowledgeAdminGuard adminGuard;
    private final KnowledgeIndexService knowledgeIndexService;
    private final AiMetrics aiMetrics;

    public ApprovalService(
            ApprovalTaskRepository repository,
            ApprovalProperties properties,
            ObjectMapper objectMapper,
            AiBusinessGateway businessGateway,
            LearningPlanWorkflowService learningPlanWorkflow,
            KnowledgeAdminGuard adminGuard,
            KnowledgeIndexService knowledgeIndexService,
            AiMetrics aiMetrics) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.businessGateway = businessGateway;
        this.learningPlanWorkflow = learningPlanWorkflow;
        this.adminGuard = adminGuard;
        this.knowledgeIndexService = knowledgeIndexService;
        this.aiMetrics = aiMetrics;
    }

    public ApprovalRequestResult request(
            ToolRunContext runContext,
            String actionType,
            Object request) {
        PreparedApproval prepared = prepare(
                runContext,
                actionType,
                request);
        ApprovalTask task = repository.createOrFind(
                runContext.run().id(),
                runContext.user().id(),
                runContext.run().requestId(),
                actionType,
                prepared.payload(),
                prepared.reason(),
                Instant.now().plus(properties.getTtl()));
        aiMetrics.approval();
        return result(task);
    }

    public List<ApprovalTask> list() {
        Long userId = UserContext.requireUser().id();
        List<ApprovalTask> tasks = repository.findByUser(
                userId,
                Math.max(1, Math.min(200, properties.getListLimit())));
        tasks.forEach(task -> expireIfNeeded(task, userId));
        return repository.findByUser(
                userId,
                Math.max(1, Math.min(200, properties.getListLimit())));
    }

    public ApprovalTask approve(UUID approvalId) {
        Long userId = UserContext.requireUser().id();
        ApprovalTask current = requireOwned(approvalId, userId);
        if ("APPROVED".equals(current.status())) {
            return current;
        }
        expireIfNeeded(current, userId);
        current = requireOwned(approvalId, userId);
        if (!current.pending()) {
            throw stateError(current);
        }
        if (!repository.claim(approvalId, userId)) {
            throw stateError(requireOwned(approvalId, userId));
        }

        try {
            Object executionResult = execute(current);
            repository.approve(
                    approvalId,
                    objectMapper.valueToTree(executionResult));
            aiMetrics.approval();
            return requireOwned(approvalId, userId);
        } catch (RuntimeException exception) {
            repository.fail(
                    approvalId,
                    errorCode(exception),
                    objectMapper.valueToTree(new ErrorResult(
                            errorCode(exception),
                            publicMessage(exception))));
            throw exception;
        }
    }

    public ApprovalTask reject(UUID approvalId) {
        Long userId = UserContext.requireUser().id();
        ApprovalTask current = requireOwned(approvalId, userId);
        if ("REJECTED".equals(current.status())) {
            return current;
        }
        expireIfNeeded(current, userId);
        current = requireOwned(approvalId, userId);
        if (!current.pending()) {
            throw stateError(current);
        }
        if (!repository.reject(approvalId, userId)) {
            throw stateError(requireOwned(approvalId, userId));
        }
        aiMetrics.approval();
        if (CREATE_PLAN.equals(current.actionType())) {
            learningPlanWorkflow.cancel(
                    uuid(current.actionPayload(), "draftId"));
        }
        return requireOwned(approvalId, userId);
    }

    private PreparedApproval prepare(
            ToolRunContext context,
            String actionType,
            Object request) {
        return switch (actionType) {
            case ADD_CART -> prepareCourseAction(
                    request,
                    "确认将课程加入购物车");
            case REMOVE_CART -> prepareCourseAction(
                    request,
                    "确认将课程从购物车移除");
            case CREATE_PLAN -> prepareLearningPlan(context, request);
            case REBUILD_INDEX -> prepareKnowledgeRebuild();
            default -> throw new BusinessOperationException(
                    "APPROVAL_ACTION_UNSUPPORTED",
                    "该写操作尚未接入审批执行器");
        };
    }

    private PreparedApproval prepareCourseAction(
            Object request,
            String reason) {
        CourseIdRequest courseRequest =
                objectMapper.convertValue(request, CourseIdRequest.class);
        CourseAiClient.CourseKnowledge course =
                businessGateway.getCourse(courseRequest.courseId());
        ObjectNode payload = objectMapper.createObjectNode()
                .put("courseId", course.id())
                .put("courseTitle", course.title());
        return new PreparedApproval(
                payload,
                reason + "：“" + course.title() + "”");
    }

    private PreparedApproval prepareLearningPlan(
            ToolRunContext context,
            Object request) {
        CreateLearningPlanRequest planRequest =
                objectMapper.convertValue(
                        request,
                        CreateLearningPlanRequest.class);
        LearningPlanDraftRecord draft =
                learningPlanWorkflow.prepare(
                        context.run().id(),
                        planRequest);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("draftId", draft.id().toString())
                .put("goal", draft.state().goal())
                .put(
                        "minutesPerDay",
                        draft.state().minutesPerDay())
                .put(
                        "planDays",
                        draft.state().draft().planDays());
        return new PreparedApproval(
                payload,
                "确认保存学习计划：“" + draft.state().goal() + "”");
    }

    private PreparedApproval prepareKnowledgeRebuild() {
        adminGuard.requireAdmin();
        return new PreparedApproval(
                objectMapper.createObjectNode(),
                "确认重建全部知识索引");
    }

    private Object execute(ApprovalTask task) {
        // Do not trust the old payload as a business fact. It only carries IDs.
        return switch (task.actionType()) {
            case ADD_CART -> {
                Long courseId = longValue(
                        task.actionPayload(),
                        "courseId");
                businessGateway.getCourse(courseId);
                var existing = businessGateway.getMyCart().stream()
                        .filter(item ->
                                courseId.equals(item.courseId()))
                        .findFirst();
                if (existing.isPresent()) {
                    yield existing.get();
                }
                yield businessGateway.addMyCartItem(courseId);
            }
            case REMOVE_CART -> {
                Long courseId = longValue(
                        task.actionPayload(),
                        "courseId");
                businessGateway.getCourse(courseId);
                boolean present = businessGateway.getMyCart().stream()
                        .anyMatch(item ->
                                courseId.equals(item.courseId()));
                if (!present) {
                    throw new BusinessOperationException(
                            "CART_ITEM_NOT_FOUND",
                            "课程当前不在购物车中");
                }
                yield businessGateway.removeMyCartItem(courseId);
            }
            case CREATE_PLAN -> learningPlanWorkflow.approve(
                    uuid(task.actionPayload(), "draftId"));
            case REBUILD_INDEX -> {
                adminGuard.requireAdmin();
                boolean started = knowledgeIndexService.startRebuild();
                yield new KnowledgeRebuildResult(
                        started,
                        knowledgeIndexService.status());
            }
            default -> throw new BusinessOperationException(
                    "APPROVAL_ACTION_UNSUPPORTED",
                    "该审批动作当前无法执行");
        };
    }

    private void expireIfNeeded(ApprovalTask task, Long userId) {
        if (task.pending()
                && !task.expiresAt().isAfter(Instant.now())) {
            repository.expire(task.id(), userId);
            if (CREATE_PLAN.equals(task.actionType())) {
                learningPlanWorkflow.cancel(
                        uuid(task.actionPayload(), "draftId"));
            }
        }
    }

    private ApprovalTask requireOwned(UUID id, Long userId) {
        return repository.findOwned(id, userId)
                .orElseThrow(ApprovalNotFoundException::new);
    }

    private ApprovalStateException stateError(ApprovalTask task) {
        return switch (task.status()) {
            case "EXPIRED" -> new ApprovalStateException(
                    "APPROVAL_EXPIRED",
                    "审批任务已过期");
            case "EXECUTING" -> new ApprovalStateException(
                    "APPROVAL_EXECUTING",
                    "审批任务正在执行");
            default -> new ApprovalStateException(
                    "APPROVAL_NOT_PENDING",
                    "审批任务当前状态不可操作：" + task.status());
        };
    }

    private ApprovalRequestResult result(ApprovalTask task) {
        return new ApprovalRequestResult(
                task.id(),
                task.actionType(),
                task.status(),
                task.reason(),
                task.expiresAt());
    }

    private Long longValue(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new BusinessOperationException(
                    "APPROVAL_PAYLOAD_INVALID",
                    "审批参数缺少 " + field);
        }
        return value.longValue();
    }

    private UUID uuid(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        try {
            return UUID.fromString(value == null ? "" : value.asText());
        } catch (IllegalArgumentException exception) {
            throw new BusinessOperationException(
                    "APPROVAL_PAYLOAD_INVALID",
                    "审批参数缺少 " + field);
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessOperationException business) {
            return business.getCode();
        }
        return exception.getClass().getSimpleName();
    }

    private String publicMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "审批执行失败，请重新发起操作"
                : message;
    }

    private record PreparedApproval(JsonNode payload, String reason) {
    }

    private record ErrorResult(String code, String message) {
    }
}
