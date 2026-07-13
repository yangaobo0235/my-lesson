package com.yangaobo.ai.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.mcp.config.McpToolProperties;
import com.yangaobo.ai.observability.AiMetrics;
import com.yangaobo.ai.tool.repository.ToolCallRepository;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class McpToolRegistry {

    public static final String MCP_TOOL_WILDCARD = "mcp:*";
    private static final String DEFAULT_MCP_SERVER_NAME = "spring-ai-mcp";

    private final McpToolProperties properties;
    private final ObjectProvider<SyncMcpToolCallbackProvider> providers;
    private final ToolCallRepository repository;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor taskExecutor;
    private final AiMetrics aiMetrics;

    public McpToolRegistry(
            McpToolProperties properties,
            ObjectProvider<SyncMcpToolCallbackProvider> providers,
            ToolCallRepository repository,
            ObjectMapper objectMapper,
            @Qualifier("businessToolTaskExecutor")
            AsyncTaskExecutor taskExecutor,
            AiMetrics aiMetrics) {
        this.properties = properties;
        this.providers = providers;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.aiMetrics = aiMetrics;
    }

    public List<ToolCallback> callbacks() {
        return callbacks(Set.of(MCP_TOOL_WILDCARD));
    }

    public List<ToolCallback> callbacks(Set<String> names) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        boolean includeAll = names.contains(MCP_TOOL_WILDCARD);
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        providers.orderedStream().forEach(provider -> {
            for (ToolCallback callback : provider.getToolCallbacks()) {
                if (!enabled(callback)
                        || !matchesRoute(callback, names, includeAll)) {
                    continue;
                }
                ToolCallback wrapped = wrap(callback);
                callbacks.putIfAbsent(
                        wrapped.getToolDefinition().name(),
                        wrapped);
            }
        });
        return new ArrayList<>(callbacks.values());
    }

    private boolean matchesRoute(
            ToolCallback callback,
            Set<String> names,
            boolean includeAll) {
        if (includeAll) {
            return true;
        }
        String originalName = callback.getToolDefinition().name();
        String prefixedName = prefixedName(originalName);
        return names.contains(originalName) || names.contains(prefixedName);
    }

    private ToolCallback wrap(ToolCallback callback) {
        ToolDefinition original = callback.getToolDefinition();
        String externalToolName = externalToolName(callback);
        ToolDefinition definition = ToolDefinition.builder()
                .name(prefixedName(original.name()))
                .description("[MCP] " + original.description())
                .inputSchema(original.inputSchema())
                .build();
        return new AuditedMcpToolCallback(
                callback,
                definition,
                externalToolName,
                DEFAULT_MCP_SERVER_NAME,
                properties,
                repository,
                objectMapper,
                taskExecutor,
                aiMetrics);
    }

    private boolean enabled(ToolCallback callback) {
        String originalName = callback.getToolDefinition().name();
        String prefixedName = prefixedName(originalName);
        Set<String> disabled = properties.getDisabledTools();
        if (disabled.contains(originalName) || disabled.contains(prefixedName)) {
            return false;
        }
        if (properties.isAllowAllTools()) {
            return true;
        }
        Set<String> allowed = properties.getAllowedTools();
        return allowed.contains(originalName) || allowed.contains(prefixedName);
    }

    private String prefixedName(String name) {
        String prefix = properties.getToolNamePrefix() == null
                ? ""
                : properties.getToolNamePrefix().trim();
        if (prefix.isBlank() || name.startsWith(prefix)) {
            return name;
        }
        return prefix + name;
    }

    private String externalToolName(ToolCallback callback) {
        if (callback instanceof SyncMcpToolCallback mcpCallback) {
            return mcpCallback.getOriginalToolName();
        }
        return callback.getToolDefinition().name();
    }
}
