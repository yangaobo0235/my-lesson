package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.conversation.model.ConversationMessage;
import com.yangaobo.ai.conversation.model.ConversationRun;
import com.yangaobo.ai.conversation.repository.ConversationMessageRepository;
import com.yangaobo.ai.conversation.repository.ConversationRepository;
import com.yangaobo.ai.conversation.repository.ConversationRunRepository;
import com.yangaobo.ai.rag.model.AiAnswer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConversationWriteService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ConversationRunRepository runRepository;
    private final String modelName;

    public ConversationWriteService(
            ConversationRepository conversationRepository,
            ConversationMessageRepository messageRepository,
            ConversationRunRepository runRepository,
            @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
            String modelName) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.modelName = modelName;
    }

    @Transactional
    public Submission createSubmission(
            UUID conversationId,
            Long userId,
            String message,
            UUID requestId,
            String traceId) {
        ConversationMessage userMessage = messageRepository.insertUser(
                conversationId,
                message,
                requestId,
                traceId);
        ConversationRun run = runRepository.insert(
                UUID.randomUUID(),
                conversationId,
                userId,
                requestId,
                userMessage.id(),
                traceId,
                modelName);
        conversationRepository.updateInitialTitle(
                conversationId,
                userId,
                title(message));
        conversationRepository.touch(conversationId);
        return new Submission(run, userMessage);
    }

    @Transactional
    public ConversationMessage complete(
            ConversationRun run,
            AiAnswer answer,
            long latencyMillis) {
        ConversationMessage assistantMessage =
                messageRepository.insertAssistant(
                        run.conversationId(),
                        answer.answer(),
                        answer.citations(),
                        run.requestId(),
                        answer.traceId());
        runRepository.markSucceeded(
                run.id(),
                assistantMessage.id(),
                latencyMillis);
        conversationRepository.touch(run.conversationId());
        return assistantMessage;
    }

    private String title(String message) {
        String normalized = message.replaceAll("\\s+", " ").trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= 30) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, 30);
        return normalized.substring(0, end) + "...";
    }

    public record Submission(
            ConversationRun run,
            ConversationMessage userMessage
    ) {
    }
}
