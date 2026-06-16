package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.conversation.config.ConversationProperties;
import com.yangaobo.ai.conversation.model.ConversationMemoryContext;
import com.yangaobo.ai.conversation.model.ConversationMessage;
import com.yangaobo.ai.conversation.repository.ConversationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationMemoryService {

    private static final Logger log =
            LoggerFactory.getLogger(ConversationMemoryService.class);

    private final ConversationMessageRepository messageRepository;
    private final ConversationSummarizer summarizer;
    private final ConversationProperties properties;

    public ConversationMemoryService(
            ConversationMessageRepository messageRepository,
            ConversationSummarizer summarizer,
            ConversationProperties properties) {
        this.messageRepository = messageRepository;
        this.summarizer = summarizer;
        this.properties = properties;
    }

    public ConversationMemoryContext prepare(
            UUID conversationId,
            UUID currentUserMessageId,
            String traceId) {
        List<ConversationMessage> recent =
                messageRepository.findRecentDialogMessages(
                        conversationId,
                        currentUserMessageId,
                        properties.getRecentMessageLimit());
        ConversationMessage storedSummary =
                messageRepository.findLatestSummary(conversationId);
        String summary = storedSummary == null
                ? ""
                : storedSummary.content();

        if (recent.size() == properties.getRecentMessageLimit()) {
            ConversationMessage cutoff = recent.get(0);
            List<ConversationMessage> older =
                    messageRepository.findMessagesForSummary(
                            conversationId,
                            storedSummary == null
                                    ? null
                                    : storedSummary.summaryUntil(),
                            storedSummary == null
                                    ? null
                                    : storedSummary.summaryUntilMessageId(),
                            cutoff,
                            properties.getSummaryBatchSize());
            if (!older.isEmpty()) {
                try {
                    summary = summarizer.summarize(summary, older);
                    messageRepository.insertSummary(
                            conversationId,
                            summary,
                            older.get(older.size() - 1).createdAt(),
                            older.get(older.size() - 1).id(),
                            traceId);
                } catch (RuntimeException exception) {
                    log.warn(
                            "Conversation summary failed, using existing memory: {}",
                            exception.getClass().getSimpleName());
                }
            }
        }
        return new ConversationMemoryContext(summary, recent);
    }
}
