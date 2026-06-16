package com.yangaobo.ai.tool.model;

import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.conversation.model.ConversationRun;
import com.yangaobo.ai.security.AuthenticatedUser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public final class ToolRunContext {

    public static final String CONTEXT_KEY =
            ToolRunContext.class.getName();

    private final AuthenticatedUser user;
    private final ConversationRun run;
    private final BiConsumer<ConversationEventType, Map<String, Object>>
            eventSink;
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicBoolean approvalPending =
            new AtomicBoolean();
    private final List<ToolStep> completedSteps =
            new CopyOnWriteArrayList<>();

    public ToolRunContext(
            AuthenticatedUser user,
            ConversationRun run,
            BiConsumer<ConversationEventType, Map<String, Object>>
                    eventSink) {
        this.user = user;
        this.run = run;
        this.eventSink = eventSink;
    }

    public AuthenticatedUser user() {
        return user;
    }

    public ConversationRun run() {
        return run;
    }

    public void toolCalled() {
        callCount.incrementAndGet();
    }

    public boolean hasToolCalls() {
        return callCount.get() > 0;
    }

    public void approvalRequested() {
        approvalPending.set(true);
    }

    public boolean hasApprovalPending() {
        return approvalPending.get();
    }

    public void toolCompleted(
            String toolName,
            boolean success,
            String errorCode) {
        completedSteps.add(new ToolStep(
                toolName,
                success,
                errorCode));
    }

    public String completedToolSummary() {
        return completedSteps.stream()
                .map(step -> step.success()
                        ? step.toolName() + " 成功"
                        : step.toolName() + " 失败"
                                + (step.errorCode() == null
                                || step.errorCode().isBlank()
                                ? ""
                                : "（" + step.errorCode() + "）"))
                .collect(Collectors.joining("；"));
    }

    public void publish(
            ConversationEventType type,
            Map<String, Object> data) {
        eventSink.accept(type, data);
    }

    private record ToolStep(
            String toolName,
            boolean success,
            String errorCode
    ) {
    }
}
