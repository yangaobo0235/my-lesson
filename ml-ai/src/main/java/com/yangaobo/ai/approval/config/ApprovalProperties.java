package com.yangaobo.ai.approval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ai.approval")
public class ApprovalProperties {

    private Duration ttl = Duration.ofMinutes(30);
    private int listLimit = 100;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public int getListLimit() {
        return listLimit;
    }

    public void setListLimit(int listLimit) {
        this.listLimit = listLimit;
    }
}
