package com.yangaobo.ai.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ai.tools")
public class BusinessToolProperties {

    private Duration readTimeout = Duration.ofSeconds(12);
    private Duration writeTimeout = Duration.ofSeconds(20);
    private int readTimeoutRetryCount = 1;
    private int maxToolCalls = 8;
    private int maxCourseSearchLimit = 10;
    private int maxRecentOrderLimit = 20;

    public Duration getTimeout() {
        return readTimeout;
    }

    public void setTimeout(Duration timeout) {
        this.readTimeout = timeout;
        this.writeTimeout = timeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public int getReadTimeoutRetryCount() {
        return readTimeoutRetryCount;
    }

    public void setReadTimeoutRetryCount(int readTimeoutRetryCount) {
        this.readTimeoutRetryCount = readTimeoutRetryCount;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
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
