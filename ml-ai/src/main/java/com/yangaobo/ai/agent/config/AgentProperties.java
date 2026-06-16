package com.yangaobo.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AgentProperties {

    private double intentConfidenceThreshold = 0.65;
    private int maxModelCalls = 6;
    private int modelRetryCount = 1;
    private Duration intentTimeout = Duration.ofSeconds(10);
    private Duration modelTimeout = Duration.ofSeconds(45);

    public double getIntentConfidenceThreshold() {
        return intentConfidenceThreshold;
    }

    public void setIntentConfidenceThreshold(
            double intentConfidenceThreshold) {
        this.intentConfidenceThreshold = intentConfidenceThreshold;
    }

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public int getModelRetryCount() {
        return modelRetryCount;
    }

    public void setModelRetryCount(int modelRetryCount) {
        this.modelRetryCount = modelRetryCount;
    }

    public Duration getModelTimeout() {
        return modelTimeout;
    }

    public void setModelTimeout(Duration modelTimeout) {
        this.modelTimeout = modelTimeout;
    }

    public Duration getIntentTimeout() {
        return intentTimeout;
    }

    public void setIntentTimeout(Duration intentTimeout) {
        this.intentTimeout = intentTimeout;
    }
}
