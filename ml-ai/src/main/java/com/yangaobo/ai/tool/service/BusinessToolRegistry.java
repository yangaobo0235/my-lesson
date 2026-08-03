package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.tool.dto.CourseIdRequest;
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

    private final BusinessToolExecutor executor;
    private final List<ToolCallback> callbacks;
    private final Map<String, ToolCallback> callbacksByName;

    public BusinessToolRegistry(
            BusinessToolExecutor executor,
            CourseTools courseTools,
            OrderTools orderTools,
            UserTools userTools,
            LearningPlanTools learningPlanTools) {
        this.executor = executor;
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
                        "get_my_profile",
                        "查询当前登录用户的脱敏个人资料。用户身份由系统注入，参数必须为空对象。",
                        NoArgsRequest.class,
                        false,
                        Set.of(),
                        userTools::getMyProfile)),
                callback(new BusinessToolSpec<>(
                        "get_learning_plan",
                        "查询当前登录用户最近创建且仍有效的学习计划，参数必须为空对象。",
                        NoArgsRequest.class,
                        false,
                        Set.of(),
                        learningPlanTools::getLearningPlan)));
        this.callbacks = List.copyOf(registered);
        Map<String, ToolCallback> indexed = new LinkedHashMap<>();
        registered.forEach(callback -> indexed.put(
                callback.getToolDefinition().name(),
                callback));
        this.callbacksByName = Map.copyOf(indexed);
    }

    public List<ToolCallback> callbacks() {
        return callbacks;
    }

    public List<ToolCallback> callbacks(Set<String> names) {
        return callbacks.stream()
                .filter(callback -> names.contains(
                        callback.getToolDefinition().name()))
                .toList();
    }

    public boolean isWriteTool(String name) {
        return false;
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
