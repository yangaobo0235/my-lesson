package com.yangaobo.ai.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "ai.mcp")
public class McpToolProperties {

    private boolean enabled = false;
    private String toolNamePrefix = "mcp_";
    private boolean allowAllTools = true;
    private Set<String> allowedTools = new LinkedHashSet<>();
    private Set<String> disabledTools = new LinkedHashSet<>();
    private Duration timeout = Duration.ofSeconds(12);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToolNamePrefix() {
        return toolNamePrefix;
    }

    public void setToolNamePrefix(String toolNamePrefix) {
        this.toolNamePrefix = toolNamePrefix;
    }

    public boolean isAllowAllTools() {
        return allowAllTools;
    }

    public void setAllowAllTools(boolean allowAllTools) {
        this.allowAllTools = allowAllTools;
    }

    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(Set<String> allowedTools) {
        this.allowedTools = allowedTools == null
                ? new LinkedHashSet<>()
                : allowedTools;
    }

    public Set<String> getDisabledTools() {
        return disabledTools;
    }

    public void setDisabledTools(Set<String> disabledTools) {
        this.disabledTools = disabledTools == null
                ? new LinkedHashSet<>()
                : disabledTools;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
