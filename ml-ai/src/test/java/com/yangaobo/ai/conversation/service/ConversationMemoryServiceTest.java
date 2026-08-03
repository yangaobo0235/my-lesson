package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.conversation.config.ConversationProperties;
import com.yangaobo.ai.conversation.model.ConversationMemoryContext;
import com.yangaobo.ai.conversation.model.ConversationMessage;
import com.yangaobo.ai.conversation.repository.ConversationMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {

    private ConversationMessageRepository messageRepository;
    private ConversationSummarizer summarizer;
    private ConversationProperties properties;
    private ConversationMemoryService memoryService;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ConversationMessageRepository.class);
        summarizer = mock(ConversationSummarizer.class);
        properties = new ConversationProperties();
        properties.setRecentMessageLimit(2);
        properties.setSummaryBatchSize(20);
        memoryService = new ConversationMemoryService(
                messageRepository,
                summarizer,
                properties);
    }

    @Test
    void shouldIncrementallySummarizeMessagesOutsideRecentWindow() {
        UUID conversationId = UUID.randomUUID();
        UUID currentMessageId = UUID.randomUUID();
        ConversationMessage firstRecent =
                message(conversationId, "USER", "最近问题", 30);
        ConversationMessage secondRecent =
                message(conversationId, "ASSISTANT", "最近回答", 40);
        ConversationMessage previousSummary = summary(
                conversationId,
                "已有摘要",
                10);
        ConversationMessage older =
                message(conversationId, "USER", "更早问题", 20);

        when(messageRepository.findRecentDialogMessages(
                conversationId,
                currentMessageId,
                2)).thenReturn(List.of(firstRecent, secondRecent));
        when(messageRepository.findLatestSummary(conversationId))
                .thenReturn(previousSummary);
        when(messageRepository.findMessagesForSummary(
                conversationId,
                previousSummary.summaryUntil(),
                previousSummary.summaryUntilMessageId(),
                firstRecent,
                20)).thenReturn(List.of(older));
        when(summarizer.summarize("已有摘要", List.of(older)))
                .thenReturn("合并后的摘要");

        ConversationMemoryContext result = memoryService.prepare(
                conversationId,
                currentMessageId,
                "trace-1");

        assertThat(result.summary()).isEqualTo("合并后的摘要");
        assertThat(result.recentMessages())
                .containsExactly(firstRecent, secondRecent);
        verify(messageRepository).insertSummary(
                conversationId,
                "合并后的摘要",
                older.createdAt(),
                older.id(),
                "trace-1");
    }

    @Test
    void shouldNotSummarizeBeforeWindowIsFull() {
        UUID conversationId = UUID.randomUUID();
        UUID currentMessageId = UUID.randomUUID();
        ConversationMessage recent =
                message(conversationId, "USER", "第一条消息", 10);
        when(messageRepository.findRecentDialogMessages(
                conversationId,
                currentMessageId,
                2)).thenReturn(List.of(recent));
        when(messageRepository.findLatestSummary(conversationId))
                .thenReturn(null);

        ConversationMemoryContext result = memoryService.prepare(
                conversationId,
                currentMessageId,
                "trace-1");

        assertThat(result.summary()).isEmpty();
        verify(summarizer, never()).summarize(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList());
    }

    private ConversationMessage message(
            UUID conversationId,
            String role,
            String content,
            long epochSecond) {
        return new ConversationMessage(
                UUID.randomUUID(),
                conversationId,
                role,
                content,
                List.of(),
                UUID.randomUUID(),
                "trace",
                null,
                null,
                Instant.ofEpochSecond(epochSecond));
    }

    private ConversationMessage summary(
            UUID conversationId,
            String content,
            long epochSecond) {
        UUID coveredMessageId = UUID.randomUUID();
        return new ConversationMessage(
                UUID.randomUUID(),
                conversationId,
                "SYSTEM_SUMMARY",
                content,
                List.of(),
                null,
                "trace",
                Instant.ofEpochSecond(epochSecond),
                coveredMessageId,
                Instant.ofEpochSecond(epochSecond + 1));
    }
}
