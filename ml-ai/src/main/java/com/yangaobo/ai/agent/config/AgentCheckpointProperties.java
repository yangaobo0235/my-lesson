package com.yangaobo.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.agent.checkpoint")
public class AgentCheckpointProperties {

    private CheckpointType type = CheckpointType.REDIS;
    private boolean fallbackToMemory = true;
    private boolean releaseThread = false;

    public CheckpointType getType() {
        return type;
    }

    public void setType(CheckpointType type) {
        this.type = type;
    }

    public boolean isFallbackToMemory() {
        return fallbackToMemory;
    }

    public void setFallbackToMemory(boolean fallbackToMemory) {
        this.fallbackToMemory = fallbackToMemory;
    }

    public boolean isReleaseThread() {
        return releaseThread;
    }

    public void setReleaseThread(boolean releaseThread) {
        this.releaseThread = releaseThread;
    }

    public enum CheckpointType {
        MEMORY,
        REDIS
    }
}
