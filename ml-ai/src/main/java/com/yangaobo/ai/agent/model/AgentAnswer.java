package com.yangaobo.ai.agent.model;

public record AgentAnswer(
        String content,
        boolean modelCallLimitReached
) {
}
