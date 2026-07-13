package com.yangaobo.ai.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.mcp.config.McpToolProperties;
import com.yangaobo.ai.observability.AiMetrics;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.tool.repository.ToolCallRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.core.task.AsyncTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AuditedMcpToolCallback implements ToolCallback {

    private static final String TOOL_SOURCE = "MCP";

    private final ToolCallback delegate;
    private final ToolDefinition definition;
    private final String externalToolName;
    private final String mcpServerName;
    private final McpToolProperties properties;
    private final ToolCallRepository repository;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor taskExecutor;
    private final AiMetrics aiMetrics;

    public AuditedMcpToolCallback(
            ToolCallback delegate,
            ToolDefinition definition,
            String externalToolName,
            String mcpServerName,
            McpToolProperties properties,
            ToolCallRepository repository,
            ObjectMapper objectMapper,
            AsyncTaskExecutor taskExecutor,
            AiMetrics aiMetrics) {
        this.delegate = delegate;
        this.definition = definition;
        this.externalToolName = externalToolName;
        this.mcpServerName = mcpServerName;
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        ToolRunContext runContext = requireRunContext(toolContext);
        String requestJson = normalizeJson(toolInput);
        UUID auditId = repository.startRead(
                runContext.run().id(),
                runContext.user().id(),
                runContext.run().requestId(),
                definition.name(),
                requestJson,
                TOOL_SOURCE,
                mcpServerName,
                externalToolName);
        runContext.toolCalled();
        publishStarted(runContext, requestJson);

        Instant startedAt = Instant.now();
        Future<String> future = taskExecutor.submit(
                () -> delegate.call(toolInput, toolContext));
        try {
            String response = future.get(
                    Math.max(1L, properties.getTimeout().toMillis()),
                    TimeUnit.MILLISECONDS);
            long latencyMillis = elapsed(startedAt);
            repository.complete(
                    auditId,
                    successJson(response),
                    latencyMillis);
            aiMetrics.toolCall(true);
            publishCompleted(
                    runContext,
                    true,
                    null,
                    latencyMillis);
            return response;
        } catch (TimeoutException exception) {
            future.cancel(true);
            return fail(
                    auditId,
                    runContext,
                    "TIMED_OUT",
                    "MCP_TOOL_TIMEOUT",
                    "MCP 工具响应超时",
                    elapsed(startedAt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return fail(
                    auditId,
                    runContext,
                    "FAILED",
                    "MCP_TOOL_INTERRUPTED",
                    "MCP 工具调用被中断",
                    elapsed(startedAt));
        } catch (ExecutionException exception) {
            return fail(
                    auditId,
                    runContext,
                    "FAILED",
                    "MCP_TOOL_FAILED",
                    "MCP 工具调用失败",
                    elapsed(startedAt));
        }
    }

    private String fail(
            UUID auditId,
            ToolRunContext runContext,
            String status,
            String errorCode,
            String message,
            long latencyMillis) {
        String response = failureJson(errorCode, message);
        repository.fail(
                auditId,
                status,
                errorCode,
                response,
                latencyMillis);
        aiMetrics.toolCall(false);
        publishCompleted(runContext, false, errorCode, latencyMillis);
        return response;
    }

    private void publishStarted(
            ToolRunContext runContext,
            String requestJson) {
        runContext.publish(
                ConversationEventType.TOOL_STARTED,
                Map.of(
                        "toolName", definition.name(),
                        "toolSource", TOOL_SOURCE,
                        "mcpServerName", mcpServerName,
                        "externalToolName", externalToolName,
                        "writeOperation", false,
                        "target", requestJson,
                        "replayed", false));
    }

    private void publishCompleted(
            ToolRunContext runContext,
            boolean success,
            String errorCode,
            long latencyMillis) {
        runContext.toolCompleted(
                definition.name(),
                success,
                errorCode);
        runContext.publish(
                ConversationEventType.TOOL_COMPLETED,
                Map.of(
                        "toolName", definition.name(),
                        "toolSource", TOOL_SOURCE,
                        "mcpServerName", mcpServerName,
                        "externalToolName", externalToolName,
                        "success", success,
                        "writeOperation", false,
                        "replayed", false,
                        "latencyMillis", latencyMillis,
                        "errorCode", errorCode == null ? "" : errorCode));
    }

    private ToolRunContext requireRunContext(ToolContext toolContext) {
        if (toolContext != null) {
            Object value = toolContext.getContext().get(
                    ToolRunContext.CONTEXT_KEY);
            if (value instanceof ToolRunContext runContext) {
                return runContext;
            }
        }
        throw new IllegalStateException(
                "MCP tool run context is missing");
    }

    private String normalizeJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String successJson(String response) {
        return writeJson(Map.of(
                "success", true,
                "toolSource", TOOL_SOURCE,
                "data", response == null ? "" : response));
    }

    private String failureJson(String errorCode, String message) {
        return writeJson(Map.of(
                "success", false,
                "toolSource", TOOL_SOURCE,
                "errorCode", errorCode,
                "message", message));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize MCP tool audit data",
                    exception);
        }
    }

    private long elapsed(Instant startedAt) {
        return Math.max(
                0L,
                Duration.between(startedAt, Instant.now()).toMillis());
    }
}
