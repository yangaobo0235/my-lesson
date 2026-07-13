package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.knowledge.service.KnowledgeAdminGuard;
import com.yangaobo.ai.mcp.tool.McpToolRegistry;
import com.yangaobo.ai.tool.dto.CourseIdRequest;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.dto.LimitRequest;
import com.yangaobo.ai.tool.dto.NoArgsRequest;
import com.yangaobo.ai.tool.dto.SearchCoursesRequest;
import com.yangaobo.ai.tool.model.BusinessToolSpec;
import com.yangaobo.ai.tool.model.ToolResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@Component
public class BusinessToolRegistry {

    private static final Set<String> WRITE_TOOL_NAMES = Set.of(
            "add_course_to_my_cart",
            "remove_course_from_my_cart",
            "create_learning_plan",
            "rebuild_knowledge_index");

    private final BusinessToolExecutor executor;
    private final McpToolRegistry mcpToolRegistry;
    private final List<ToolCallback> callbacks;
    private final Map<String, ToolCallback> callbacksByName;

    public BusinessToolRegistry(
            BusinessToolExecutor executor,
            CourseTools courseTools,
            OrderTools orderTools,
            UserTools userTools,
            LearningPlanTools learningPlanTools,
            KnowledgeTools knowledgeTools,
            KnowledgeAdminGuard knowledgeAdminGuard,
            McpToolRegistry mcpToolRegistry) {
        this.executor = executor;
        this.mcpToolRegistry = mcpToolRegistry;
        List<ToolCallback> registered = List.of(
                callback(new BusinessToolSpec<>(
                        "search_courses",
                        "按关键词搜索当前课程。适合课程推荐、确认课程是否存在或先查找课程 ID。"
                                + " keyword 必填，limit 默认 5 且最大 10。",
                        SearchCoursesRequest.class,
                        false,
                        Set.of(),
                        courseTools::searchCourses)),
                callback(new BusinessToolSpec<>(
                        "get_course_detail",
                        "根据 courseId 查询课程详情、作者、分类、介绍和分集信息。",
                        CourseIdRequest.class,
                        false,
                        Set.of(),
                        courseTools::getCourseDetail)),
                callback(new BusinessToolSpec<>(
                        "get_my_recent_orders",
                        "查询当前登录用户最近的订单。limit 默认 5 且最大 20。"
                                + " 用户身份由系统注入，禁止传 userId。",
                        LimitRequest.class,
                        false,
                        Set.of(),
                        orderTools::getMyRecentOrders)),
                callback(new BusinessToolSpec<>(
                        "get_my_cart",
                        "查询当前登录用户的购物车。用户身份由系统注入，参数必须为空对象。",
                        NoArgsRequest.class,
                        false,
                        Set.of(),
                        orderTools::getMyCart)),
                callback(new BusinessToolSpec<>(
                        "add_course_to_my_cart",
                        "将指定 courseId 的课程加入当前登录用户购物车。"
                                + " 只有用户明确要求加入时才能调用；调用后创建审批任务，不会立即写入。",
                        CourseIdRequest.class,
                        true,
                        Set.of(),
                        orderTools::addCourseToMyCart)),
                callback(new BusinessToolSpec<>(
                        "remove_course_from_my_cart",
                        "从当前登录用户购物车移除指定 courseId 的课程。"
                                + " 只有用户明确要求移除时才能调用；调用后创建审批任务，不会立即写入。",
                        CourseIdRequest.class,
                        true,
                        Set.of(),
                        orderTools::removeCourseFromMyCart)),
                callback(new BusinessToolSpec<>(
                        "get_my_profile",
                        "查询当前登录用户的脱敏个人资料。用户身份由系统注入，参数必须为空对象。",
                        NoArgsRequest.class,
                        false,
                        Set.of(),
                        userTools::getMyProfile)),
                callback(new BusinessToolSpec<>(
                        "create_learning_plan",
                        "根据当前登录用户明确提供的学习目标创建并保存学习计划。"
                                + " goal 必填，availableMinutesPerDay 可选，范围 10 到 480。"
                                + " 先生成并校验草案，再创建审批任务；批准前不会保存正式计划。",
                        CreateLearningPlanRequest.class,
                        true,
                        Set.of(),
                        learningPlanTools::createLearningPlan)),
                callback(new BusinessToolSpec<>(
                        "get_learning_plan",
                        "查询当前登录用户最近创建且仍有效的学习计划，参数必须为空对象。",
                        NoArgsRequest.class,
                        false,
                        Set.of(),
                        learningPlanTools::getLearningPlan)),
                callback(new BusinessToolSpec<>(
                        "rebuild_knowledge_index",
                        "启动全量知识索引重建并返回当前状态。"
                                + " 仅管理员可用，调用后创建审批任务，批准后才启动重建。",
                        NoArgsRequest.class,
                        true,
                        knowledgeAdminGuard.allowedRoles(),
                        knowledgeTools::rebuildKnowledgeIndex)));
        this.callbacks = List.copyOf(registered);
        Map<String, ToolCallback> indexed = new LinkedHashMap<>();
        registered.forEach(callback -> indexed.put(
                callback.getToolDefinition().name(),
                callback));
        this.callbacksByName = Map.copyOf(indexed);
    }

    public List<ToolCallback> callbacks() {
        List<ToolCallback> mcpCallbacks = mcpToolRegistry.callbacks();
        if (mcpCallbacks.isEmpty()) {
            return callbacks;
        }
        java.util.ArrayList<ToolCallback> result =
                new java.util.ArrayList<>(callbacks);
        result.addAll(mcpCallbacks);
        return List.copyOf(result);
    }

    public List<ToolCallback> callbacks(
            Set<String> names,
            boolean allowWrites) {
        java.util.ArrayList<ToolCallback> result = callbacks.stream()
                .filter(callback -> names.contains(
                        callback.getToolDefinition().name()))
                .filter(callback -> allowWrites
                        || !WRITE_TOOL_NAMES.contains(
                                callback.getToolDefinition().name()))
                .collect(java.util.stream.Collectors.toCollection(
                        java.util.ArrayList::new));
        if (!names.isEmpty()) {
            result.addAll(mcpToolRegistry.callbacks(names));
        }
        return List.copyOf(result);
    }

    public boolean isWriteTool(String name) {
        return WRITE_TOOL_NAMES.contains(name);
    }

    public boolean contains(String name) {
        return callbacksByName.containsKey(name);
    }

    private <I, O> ToolCallback callback(
            BusinessToolSpec<I, O> spec) {
        BiFunction<I, ToolContext, ToolResult<O>> function =
                (request, context) ->
                        executor.execute(spec, request, context);
        return FunctionToolCallback
                .<I, ToolResult<O>>builder(spec.name(), function)
                .description(spec.description())
                .inputType(spec.inputType())
                .build();
    }
}
