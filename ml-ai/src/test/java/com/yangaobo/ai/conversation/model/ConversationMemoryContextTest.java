package com.yangaobo.ai.conversation.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryContextTest {

    @Test
    void shouldUseRecentUserMessagesToResolveFollowUpRetrieval() {
        ConversationMemoryContext context = new ConversationMemoryContext(
                "用户正在比较摄影课程。",
                List.of(
                        message("USER", "把日常拍成电影适合新手吗"),
                        message("ASSISTANT", "适合。",
                                "trace-1"),
                        message("USER", "镜头里的青春纪念册呢")));

        assertThat(context.retrievalQuery("它们有什么区别"))
                .contains(
                        "它们有什么区别",
                        "把日常拍成电影适合新手吗",
                        "镜头里的青春纪念册呢");
        assertThat(context.promptContext())
                .contains("历史摘要", "最近对话", "ASSISTANT：适合。");
    }

    private ConversationMessage message(String role, String content) {
        return message(role, content, null);
    }

    private ConversationMessage message(
            String role,
            String content,
            String traceId) {
        return new ConversationMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                role,
                content,
                List.of(),
                UUID.randomUUID(),
                traceId,
                null,
                null,
                Instant.now());
    }
}
