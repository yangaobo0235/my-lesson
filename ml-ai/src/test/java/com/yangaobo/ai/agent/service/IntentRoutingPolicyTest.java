package com.yangaobo.ai.agent.service;

import com.yangaobo.ai.agent.config.AgentProperties;
import com.yangaobo.ai.agent.model.AgentRoute;
import com.yangaobo.ai.agent.model.IntentDecision;
import com.yangaobo.ai.agent.model.UserIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRoutingPolicyTest {

    private final AgentProperties properties = new AgentProperties();
    private final IntentRoutingPolicy policy =
            new IntentRoutingPolicy(properties);

    @Test
    void shouldAllowOnlyReadToolsForLowConfidenceIntent() {
        AgentRoute route = policy.route(new IntentDecision(
                UserIntent.OUT_OF_SCOPE,
                0.64,
                "uncertain"));

        assertThat(route.conservative()).isTrue();
        assertThat(route.retrievalEnabled()).isTrue();
        assertThat(route.toolNames()).contains(
                "search_courses",
                "get_course_detail",
                "get_my_recent_orders",
                "get_my_cart",
                "get_my_profile",
                "get_learning_plan");
    }

    @Test
    void shouldExposeOnlyTheMinimumToolsForEachScenario() {
        AgentRoute route = policy.route(new IntentDecision(
                UserIntent.LEARNING_PLAN,
                0.9,
                "explicit learning plan"));

        assertThat(route.conservative()).isFalse();
        assertThat(route.retrievalEnabled()).isTrue();
        assertThat(route.toolNames()).containsExactlyInAnyOrder(
                "search_courses",
                "get_course_detail",
                "get_my_profile",
                "get_learning_plan");
        assertThat(policy.route(new IntentDecision(
                UserIntent.OUT_OF_SCOPE,
                0.9,
                "unrelated")).toolNames()).isEmpty();
    }
}
