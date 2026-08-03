package com.yangaobo.ai.agent.service;

import com.yangaobo.ai.agent.config.AgentProperties;
import com.yangaobo.ai.agent.model.AgentRoute;
import com.yangaobo.ai.agent.model.IntentDecision;
import com.yangaobo.ai.agent.model.UserIntent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class IntentRoutingPolicy {

    private static final Set<String> READ_ONLY_TOOLS = Set.of(
            "search_courses",
            "get_course_detail",
            "get_my_recent_orders",
            "get_my_cart",
            "get_my_profile",
            "get_learning_plan");

    private static final Map<UserIntent, Set<String>> TOOLS_BY_INTENT =
            Map.of(
                    UserIntent.KNOWLEDGE_QA,
                    Set.of(
                            "search_courses",
                            "get_course_detail"),
                    UserIntent.COURSE_SEARCH,
                    Set.of(
                            "search_courses",
                            "get_course_detail",
                            "get_my_profile"),
                    UserIntent.PERSONAL_QUERY,
                    Set.of(
                            "get_my_recent_orders",
                            "get_my_cart",
                            "get_my_profile",
                            "get_learning_plan"),
                    UserIntent.LEARNING_PLAN,
                    Set.of(
                            "search_courses",
                            "get_course_detail",
                            "get_my_profile",
                            "get_learning_plan"),
                    UserIntent.OUT_OF_SCOPE,
                    Set.of());

    private final AgentProperties properties;

    public IntentRoutingPolicy(AgentProperties properties) {
        this.properties = properties;
    }

    public AgentRoute route(IntentDecision decision) {
        IntentDecision normalized = decision.normalized();
        if (normalized.confidence()
                < properties.getIntentConfidenceThreshold()) {
            return new AgentRoute(true, true, READ_ONLY_TOOLS);
        }
        UserIntent intent = normalized.intent();
        boolean retrievalEnabled = switch (intent) {
            case KNOWLEDGE_QA, COURSE_SEARCH, LEARNING_PLAN -> true;
            case PERSONAL_QUERY, OUT_OF_SCOPE -> false;
        };
        return new AgentRoute(
                retrievalEnabled,
                false,
                TOOLS_BY_INTENT.getOrDefault(intent, Set.of()));
    }
}
