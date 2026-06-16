package com.yangaobo.ai.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ai.tools")
public class BusinessToolProperties {

    private Duration timeout = Duration.ofSeconds(12);
    private int readTimeoutRetryCount = 1;
    private int maxCourseSearchLimit = 10;
    private int maxRecentOrderLimit = 20;

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getReadTimeoutRetryCount() {
        return readTimeoutRetryCount;
    }

    public void setReadTimeoutRetryCount(int readTimeoutRetryCount) {
        this.readTimeoutRetryCount = readTimeoutRetryCount;
    }

    public int getMaxCourseSearchLimit() {
        return maxCourseSearchLimit;
    }

    public void setMaxCourseSearchLimit(int maxCourseSearchLimit) {
        this.maxCourseSearchLimit = maxCourseSearchLimit;
    }

    public int getMaxRecentOrderLimit() {
        return maxRecentOrderLimit;
    }

    public void setMaxRecentOrderLimit(int maxRecentOrderLimit) {
        this.maxRecentOrderLimit = maxRecentOrderLimit;
    }
}
