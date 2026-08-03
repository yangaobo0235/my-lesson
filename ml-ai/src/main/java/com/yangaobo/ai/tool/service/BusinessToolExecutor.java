package com.yangaobo.ai.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.exception.DownstreamServiceException;
import com.yangaobo.ai.knowledge.exception.AdminAccessDeniedException;
import com.yangaobo.ai.observability.AiLogContext;
import com.yangaobo.ai.observability.AiMetrics;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.tool.config.BusinessToolProperties;
import com.yangaobo.ai.tool.model.BusinessToolSpec;
import com.yangaobo.ai.tool.model.ToolCallRecord;
import com.yangaobo.ai.tool.model.ToolResult;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.tool.repository.ToolCallRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
public class BusinessToolExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(BusinessToolExecutor.class);

    private final ToolCallRepository toolCallRepository;
    private final ToolDataSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final BusinessToolProperties properties;
    private final AsyncTaskExecutor taskExecutor;
    private final AiMetrics aiMetrics;

    public BusinessToolExecutor(
            ToolCallRepository toolCallRepository,
            ToolDataSanitizer sanitizer,
            ObjectMapper objectMapper,
            Validator validator,
            BusinessToolProperties properties,
            @Qualifier("businessToolTaskExecutor")
            AsyncTaskExecutor taskExecutor,
            AiMetrics aiMetrics) {
        this.toolCallRepository = toolCallRepository;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        this.aiMetrics = aiMetrics;
    }

    public <I, O> ToolResult<O> execute(
            BusinessToolSpec<I, O> spec,
            I request,
            ToolContext springToolContext) {
        ToolRunContext runContext = requireRunContext(springToolContext);
        runContext.toolCalled();
        if (runContext.toolCallLimitExceeded(properties.getMaxToolCalls())) {
            return ToolResult.failure(
                    "TOOL_CALL_LIMIT_REACHED",
                    "已达到本轮最多工具调用次数，请缩小问题范围后重试");
        }
        String requestJson = writeJson(sanitizer.sanitize(request));
        UUID auditId;
        if (spec.writeOperation()) {
            Optional<UUID> reservation = toolCallRepository.reserveWrite(
                    runContext.run().id(),
                    runContext.user().id(),
                    runContext.run().requestId(),
                    spec.name(),
                    requestJson);
            if (reservation.isEmpty()) {
                ToolResult<O> replayed =
                        replayWrite(runContext, spec);
                publishStarted(
                        runContext,
                        spec,
                        request,
                        true);
                publishCompleted(
                        runContext,
                        spec,
                        replayed,
                        0L,
                        true);
                return replayed;
            }
            auditId = reservation.get();
        } else {
            auditId = toolCallRepository.startRead(
                    runContext.run().id(),
                    runContext.user().id(),
                    runContext.run().requestId(),
                    spec.name(),
                    requestJson);
        }

        publishStarted(
                runContext,
                spec,
                request,
                false);
        ToolResult<O> validationFailure = validate(spec, request);
        if (validationFailure != null) {
            return reject(
                    auditId,
                    runContext,
                    spec,
                    validationFailure);
        }

        ToolResult<O> permissionFailure =
                checkPermission(spec, runContext.user());
        if (permissionFailure != null) {
            return reject(
                    auditId,
                    runContext,
                    spec,
                    permissionFailure);
        }

        Instant startedAt = Instant.now();
        int maxAttempts = spec.writeOperation()
                ? 1
                : Math.max(
                        1,
                        properties.getReadTimeoutRetryCount() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Future<O> future = taskExecutor.submit(
                    () -> executeAction(
                            runContext,
                            spec,
                            request));
            try {
                O data = future.get(
                        timeout(spec).toMillis(),
                        TimeUnit.MILLISECONDS);
                long latencyMillis = elapsed(startedAt);
                ToolResult<O> result = ToolResult.success(data);
                toolCallRepository.complete(
                        auditId,
                        writeJson(sanitizer.sanitize(result)),
                        latencyMillis);
                publishCompleted(
                        runContext,
                        spec,
                        result,
                        latencyMillis,
                        false);
                return result;
            } catch (TimeoutException exception) {
                future.cancel(true);
                if (attempt < maxAttempts) {
                    log.warn(
                            "Read tool {} timed out, retrying once",
                            spec.name());
                    continue;
                }
                if (spec.writeOperation()) {
                    ToolResult<O> existing =
                            replayWrite(runContext, spec);
                    if (existing.replayed()) {
                        publishCompleted(
                                runContext,
                                spec,
                                existing,
                                elapsed(startedAt),
                                true);
                        return existing;
                    }
                }
                return fail(
                        auditId,
                        runContext,
                        spec,
                        "TIMED_OUT",
                        "TOOL_TIMEOUT",
                        "业务工具响应超时，请稍后重试",
                        elapsed(startedAt));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return fail(
                        auditId,
                        runContext,
                        spec,
                        "FAILED",
                        "TOOL_INTERRUPTED",
                        "业务工具调用被中断，请稍后重试",
                        elapsed(startedAt));
            } catch (ExecutionException exception) {
                return handleExecutionFailure(
                        auditId,
                        runContext,
                        spec,
                        exception.getCause(),
                        elapsed(startedAt));
            }
        }
        throw new IllegalStateException(
                "Business tool execution ended unexpectedly");
    }

    private <I, O> ToolResult<O> reject(
            UUID auditId,
            ToolRunContext runContext,
            BusinessToolSpec<I, O> spec,
            ToolResult<O> result) {
        toolCallRepository.fail(
                auditId,
                "FAILED",
                result.errorCode(),
                writeJson(sanitizer.sanitize(result)),
                0L);
        publishCompleted(
                runContext,
                spec,
                result,
                0L,
                false);
        return result;
    }

    @SuppressWarnings("unchecked")
    private <I, O> O executeAction(
            ToolRunContext runContext,
            BusinessToolSpec<I, O> spec,
            I request) {
        UserContext.set(runContext.user());
        AiLogContext.open(runContext.user().id(), runContext.run());
        AiLogContext.tool(spec.name());
        try {
            return spec.action().apply(request);
        } finally {
            UserContext.clear();
            AiLogContext.close();
        }
    }

    private <I, O> ToolResult<O> validate(
            BusinessToolSpec<I, O> spec,
            I request) {
        if (request == null) {
            return ToolResult.failure(
                    "INVALID_ARGUMENT",
                    spec.name() + " 缺少请求参数");
        }
        Set<ConstraintViolation<I>> violations =
                validator.validate(request);
        if (violations.isEmpty()) {
            return null;
        }
        String message = violations.stream()
                .map(violation ->
                        violation.getPropertyPath()
                                + " "
                                + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ToolResult.failure("INVALID_ARGUMENT", message);
    }

    private <I, O> ToolResult<O> checkPermission(
            BusinessToolSpec<I, O> spec,
            AuthenticatedUser user) {
        if (user == null) {
            return ToolResult.failure(
                    "AUTHENTICATION_REQUIRED",
                    "需要登录后才能使用该工具");
        }
        if (spec.allowedRoles().isEmpty()
                || spec.allowedRoles().stream().anyMatch(user::hasRole)) {
            return null;
        }
        return ToolResult.failure(
                "TOOL_FORBIDDEN",
                "当前用户无权执行该工具");
    }

    @SuppressWarnings("unchecked")
    private <I, O> ToolResult<O> replayWrite(
            ToolRunContext runContext,
            BusinessToolSpec<I, O> spec) {
        Optional<ToolCallRecord> existing =
                toolCallRepository.findWrite(
                        runContext.run().requestId(),
                        spec.name());
        if (existing.isEmpty()
                || "STARTED".equals(existing.get().status())) {
            return ToolResult.failure(
                    "TOOL_ALREADY_RUNNING",
                    "相同请求的工具操作正在执行");
        }
        if (existing.get().responseJson() == null) {
            return ToolResult.failure(
                    existing.get().errorCode() == null
                            ? "TOOL_ALREADY_EXECUTED"
                            : existing.get().errorCode(),
                    "相同请求的工具操作已经执行");
        }
        try {
            ToolResult<Object> result = objectMapper.readValue(
                    existing.get().responseJson(),
                    new TypeReference<>() {
                    });
            return (ToolResult<O>) result.asReplayed();
        } catch (JsonProcessingException exception) {
            return ToolResult.failure(
                    "TOOL_RESULT_UNAVAILABLE",
                    "历史工具结果暂时无法读取");
        }
    }

    private <I, O> ToolResult<O> handleExecutionFailure(
            UUID auditId,
            ToolRunContext runContext,
            BusinessToolSpec<I, O> spec,
            Throwable cause,
            long latencyMillis) {
        if (cause instanceof BusinessOperationException exception) {
            return fail(
                    auditId,
                    runContext,
                    spec,
                    "FAILED",
                    "BUSINESS_" + exception.getCode(),
                    exception.getMessage(),
                    latencyMillis);
        }
        if (cause instanceof DownstreamServiceException) {
            return fail(
                    auditId,
                    runContext,
                    spec,
                    "FAILED",
                    "SERVICE_UNAVAILABLE",
                    "业务服务暂时不可用，请稍后重试",
                    latencyMillis);
        }
        if (cause instanceof AdminAccessDeniedException) {
            return fail(
                    auditId,
                    runContext,
                    spec,
                    "FAILED",
                    "TOOL_FORBIDDEN",
                    "当前用户无权执行该工具",
                    latencyMillis);
        }
        log.warn(
                "Business tool {} failed with {}",
                spec.name(),
                cause == null
                        ? "UnknownException"
                        : cause.getClass().getSimpleName());
        return fail(
                auditId,
                runContext,
                spec,
                "FAILED",
                "TOOL_FAILED",
                "业务工具执行失败，请稍后重试",
                latencyMillis);
    }

    private <I, O> ToolResult<O> fail(
            UUID auditId,
            ToolRunContext runContext,
            BusinessToolSpec<I, O> spec,
            String status,
            String errorCode,
            String message,
            long latencyMillis) {
        ToolResult<O> result =
                ToolResult.failure(errorCode, message);
        toolCallRepository.fail(
                auditId,
                status,
                errorCode,
                writeJson(sanitizer.sanitize(result)),
                latencyMillis);
        publishCompleted(
                runContext,
                spec,
                result,
                latencyMillis,
                false);
        return result;
    }

    private void publishStarted(
            ToolRunContext runContext,
            BusinessToolSpec<?, ?> spec,
            Object request,
            boolean replayed) {
        Object target = sanitizer.sanitize(request);
        runContext.publish(
                ConversationEventType.TOOL_STARTED,
                Map.of(
                        "toolName", spec.name(),
                        "writeOperation", spec.writeOperation(),
                        "target", target == null ? Map.of() : target,
                        "replayed", replayed));
    }

    private void publishCompleted(
            ToolRunContext runContext,
            BusinessToolSpec<?, ?> spec,
            ToolResult<?> result,
            long latencyMillis,
            boolean replayed) {
        runContext.toolCompleted(
                spec.name(),
                result.success(),
                result.errorCode());
        aiMetrics.toolCall(result.success());
        runContext.publish(
                ConversationEventType.TOOL_COMPLETED,
                Map.of(
                        "toolName", spec.name(),
                        "success", result.success(),
                        "writeOperation", spec.writeOperation(),
                        "replayed", replayed,
                        "latencyMillis", latencyMillis,
                        "errorCode", result.errorCode() == null
                                ? ""
                                : result.errorCode()));
    }

    private ToolRunContext requireRunContext(
            ToolContext toolContext) {
        if (toolContext == null) {
            throw new IllegalStateException(
                    "Business tool context is missing");
        }
        Object value = toolContext.getContext().get(
                ToolRunContext.CONTEXT_KEY);
        if (value instanceof ToolRunContext runContext) {
            return runContext;
        }
        throw new IllegalStateException(
                "Business tool run context is missing");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize tool audit data",
                    exception);
        }
    }

    private long elapsed(Instant startedAt) {
        return Math.max(
                0L,
                Duration.between(startedAt, Instant.now()).toMillis());
    }

    private Duration timeout(BusinessToolSpec<?, ?> spec) {
        return spec.writeOperation()
                ? properties.getWriteTimeout()
                : properties.getReadTimeout();
    }
}
