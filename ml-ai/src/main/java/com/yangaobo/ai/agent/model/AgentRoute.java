package com.yangaobo.ai.agent.model;

import java.util.Set;

public record AgentRoute(
        boolean retrievalEnabled,
        boolean conservative,
        Set<String> toolNames
) {

    public AgentRoute {
        toolNames = Set.copyOf(toolNames);
    }
}
