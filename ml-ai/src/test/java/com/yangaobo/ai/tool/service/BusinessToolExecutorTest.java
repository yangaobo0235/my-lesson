package com.yangaobo.ai.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.conversation.model.ConversationRun;
import com.yangaobo.ai.observability.AiMetrics;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.tool.config.BusinessToolProperties;
import com.yangaobo.ai.tool.dto.SearchCoursesRequest;
import com.yangaobo.ai.tool.model.BusinessToolSpec;
import com.yangaobo.ai.tool.model.ToolCallRecord;
import com.yangaobo.ai.tool.model.ToolResult;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.tool.repository.ToolCallRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessToolExecutorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final ToolCallRepository repository =
            mock(ToolCallRepository.class);
    private final BusinessToolExecutor executor =
            new BusinessToolExecutor(
                    repository,
                    new ToolDataSanitizer(objectMapper),
                    objectMapper,
                    Validation.buildDefaultValidatorFactory()
                            .getValidator(),
                    new BusinessToolProperties(),
                    new TaskExecutorAdapter(Runnable::run),
                    mock(AiMetrics.class));

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldPropagateUserAndAuditSuccessfulRead() {
        UUID auditId = UUID.randomUUID();
        when(repository.startRead(
                eq(run().id()),
                eq(41L),
                eq(run().requestId()),
                eq("search_courses"),
                anyString()))
                .thenReturn(auditId);
        List<ConversationEventType> events = new ArrayList<>();
        ToolRunContext runContext = context(events);
        BusinessToolSpec<SearchCoursesRequest, Long> spec =
                new BusinessToolSpec<>(
                        "search_courses",
                        "test",
                        SearchCoursesRequest.class,
                        false,
                        Set.of(),
                        request -> UserContext.requireUser().id());

        ToolResult<Long> result = executor.execute(
                spec,
                new SearchCoursesRequest("摄影", 5),
                springContext(runContext));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(41L);
        assertThat(UserContext.get()).isNull();
        assertThat(events).containsExactly(
                ConversationEventType.TOOL_STARTED,
                ConversationEventType.TOOL_COMPLETED);
        verify(repository).complete(
                eq(auditId),
                anyString(),
                anyLong());
    }

    @Test
    void shouldAuditValidationFailureWithoutExecutingAction() {
        UUID auditId = UUID.randomUUID();
        ConversationRun run = run();
        when(repository.startRead(
                eq(run.id()),
                eq(41L),
                eq(run.requestId()),
                eq("search_courses"),
                anyString()))
                .thenReturn(auditId);
        AtomicInteger calls = new AtomicInteger();
        BusinessToolSpec<SearchCoursesRequest, Integer> spec =
                new BusinessToolSpec<>(
                        "search_courses",
                        "test",
                        SearchCoursesRequest.class,
                        false,
                        Set.of(),
                        request -> calls.incrementAndGet());

        ToolResult<Integer> result = executor.execute(
                spec,
                new SearchCoursesRequest(" ", 5),
                springContext(context(new ArrayList<>())));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(calls).hasValue(0);
        verify(repository).fail(
                eq(auditId),
                eq("FAILED"),
                eq("INVALID_ARGUMENT"),
                anyString(),
                eq(0L));
    }

    @Test
    void shouldReplayExistingWriteWithoutExecutingItAgain() {
        ConversationRun run = run();
        when(repository.reserveWrite(
                eq(run.id()),
                eq(41L),
                eq(run.requestId()),
                eq("write_tool"),
                anyString()))
                .thenReturn(Optional.empty());
        when(repository.findWrite(run.requestId(), "write_tool"))
                .thenReturn(Optional.of(new ToolCallRecord(
                        UUID.randomUUID(),
                        "SUCCEEDED",
                        true,
                        """
                        {"success":true,"data":"done","errorCode":null,
                         "message":null,"replayed":false}
                        """,
                        null)));
        AtomicInteger calls = new AtomicInteger();
        BusinessToolSpec<SearchCoursesRequest, String> spec =
                new BusinessToolSpec<>(
                        "write_tool",
                        "test",
                        SearchCoursesRequest.class,
                        true,
                        Set.of(),
                        request -> {
                            calls.incrementAndGet();
                            return "new";
                        });

        ToolResult<String> result = executor.execute(
                spec,
                new SearchCoursesRequest("摄影", 5),
                springContext(context(new ArrayList<>())));

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(calls).hasValue(0);
        verify(repository, never()).complete(
                any(UUID.class),
                anyString(),
                anyLong());
    }

    @Test
    void shouldRetryReadToolOnceAfterTimeout() {
        UUID auditId = UUID.randomUUID();
        ConversationRun run = run();
        when(repository.startRead(
                eq(run.id()),
                eq(41L),
                eq(run.requestId()),
                eq("read_tool"),
                anyString()))
                .thenReturn(auditId);
        BusinessToolProperties retryProperties =
                new BusinessToolProperties();
        retryProperties.setTimeout(Duration.ofMillis(20));
        retryProperties.setReadTimeoutRetryCount(1);
        BusinessToolExecutor retryingExecutor =
                new BusinessToolExecutor(
                        repository,
                        new ToolDataSanitizer(objectMapper),
                        objectMapper,
                        Validation.buildDefaultValidatorFactory()
                                .getValidator(),
                        retryProperties,
                        new SimpleAsyncTaskExecutor(
                                "tool-retry-test-"),
                        mock(AiMetrics.class));
        AtomicInteger calls = new AtomicInteger();
        BusinessToolSpec<SearchCoursesRequest, Integer> spec =
                new BusinessToolSpec<>(
                        "read_tool",
                        "test",
                        SearchCoursesRequest.class,
                        false,
                        Set.of(),
                        request -> {
                            int call = calls.incrementAndGet();
                            if (call == 1) {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            return call;
                        });

        ToolResult<Integer> result = retryingExecutor.execute(
                spec,
                new SearchCoursesRequest("摄影", 5),
                springContext(context(new ArrayList<>())));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(2);
        assertThat(calls).hasValue(2);
    }

    private ToolRunContext context(List<ConversationEventType> events) {
        return new ToolRunContext(
                new AuthenticatedUser(
                        41L,
                        "alice",
                        List.of("student")),
                run(),
                (type, data) -> events.add(type));
    }

    private ToolContext springContext(ToolRunContext runContext) {
        return new ToolContext(Map.of(
                ToolRunContext.CONTEXT_KEY,
                runContext));
    }

    private ConversationRun run() {
        return RunHolder.RUN;
    }

    private static final class RunHolder {
        private static final ConversationRun RUN =
                new ConversationRun(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RUNNING",
                        UUID.randomUUID(),
                        null,
                        "trace-1",
                        null);
    }
}
