package com.yangaobo.ai.agent.model;

public record AgentProfile(
        String name,
        String displayName,
        String description,
        String systemPrompt
) {
}
