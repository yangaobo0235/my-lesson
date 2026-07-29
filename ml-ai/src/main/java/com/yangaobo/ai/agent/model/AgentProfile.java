package com.yangaobo.ai.agent.model;

public record AgentProfile(
        String name,
        String displayName,
        String description,
        String systemPrompt,
        String version
) {

    public AgentProfile(
            String name,
            String displayName,
            String description,
            String systemPrompt) {
        this(name, displayName, description, systemPrompt, "v1");
    }
}
