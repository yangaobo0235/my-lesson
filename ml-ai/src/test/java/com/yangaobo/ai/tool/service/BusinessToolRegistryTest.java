package com.yangaobo.ai.tool.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BusinessToolRegistryTest {

    @Test
    void shouldExposeStableToolsAndSafeSchemas() {
        BusinessToolRegistry registry = new BusinessToolRegistry(
                mock(BusinessToolExecutor.class),
                mock(CourseTools.class),
                mock(OrderTools.class),
                mock(UserTools.class),
                mock(LearningPlanTools.class));

        List<ToolCallback> callbacks = registry.callbacks();
        Set<String> names = callbacks.stream()
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
                "search_courses",
                "get_course_detail",
                "get_my_recent_orders",
                "get_my_cart",
                "get_my_profile",
                "get_learning_plan");
        assertThat(registry.callbacks(Set.of(
                "search_courses",
                "get_learning_plan")))
                .extracting(callback ->
                        callback.getToolDefinition().name())
                .containsExactly(
                        "search_courses",
                        "get_learning_plan");
        assertThat(callbacks).allSatisfy(callback -> {
            String schema = callback.getToolDefinition()
                    .inputSchema()
                    .toLowerCase();
            assertThat(schema)
                    .doesNotContain(
                            "userid",
                            "role",
                            "internaltoken",
                            "price",
                            "orderowner");
        });
    }
}
