package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.agent.service.IntentClassifier;
import com.yangaobo.ai.conversation.config.ConversationProperties;
import com.yangaobo.ai.conversation.dto.MessageSubmissionResponse;
import com.yangaobo.ai.conversation.dto.SendMessageRequest;
import com.yangaobo.ai.conversation.exception.ConversationBusyException;
import com.yangaobo.ai.conversation.exception.ConversationNotFoundException;
import com.yangaobo.ai.conversation.model.Conversation;
import com.yangaobo.ai.conversation.model.ConversationRun;
import com.yangaobo.ai.conversation.repository.ConversationMessageRepository;
import com.yangaobo.ai.conversation.repository.ConversationRepository;
import com.yangaobo.ai.conversation.repository.ConversationRunRepository;
import com.yangaobo.ai.rag.service.KnowledgeAnswerService;
import com.yangaobo.ai.observability.AiMetrics;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    private ConversationRepository conversationRepository;
    private ConversationRunRepository runRepository;
    private ConversationLock conversationLock;
    private ConversationWriteService writeService;
    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        ConversationMessageRepository messageRepository =
                mock(ConversationMessageRepository.class);
        runRepository = mock(ConversationRunRepository.class);
        writeService = mock(ConversationWriteService.class);
        ConversationMemoryService memoryService =
                mock(ConversationMemoryService.class);
        conversationLock = mock(ConversationLock.class);
        ConversationEventPublisher eventPublisher =
                mock(ConversationEventPublisher.class);
        KnowledgeAnswerService answerService =
                mock(KnowledgeAnswerService.class);
        IntentClassifier intentClassifier =
                mock(IntentClassifier.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        conversationService = new ConversationService(
                conversationRepository,
                messageRepository,
                runRepository,
                writeService,
                memoryService,
                conversationLock,
                eventPublisher,
                answerService,
                intentClassifier,
                new ConversationProperties(),
                mock(AiMetrics.class),
                taskExecutor);
        UserContext.set(new AuthenticatedUser(
                41L,
                "alice",
                List.of("student")));
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldReturnExistingRunForDuplicateRequestId() {
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ConversationRun existing = new ConversationRun(
                UUID.randomUUID(),
                conversationId,
                requestId,
                "SUCCEEDED",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "trace-1",
                null);
        when(conversationRepository.findOwned(conversationId, 41L))
                .thenReturn(Optional.of(conversation(conversationId)));
        when(runRepository.findByRequest(conversationId, requestId))
                .thenReturn(Optional.of(existing));

        MessageSubmissionResponse response = conversationService.submit(
                conversationId,
                new SendMessageRequest("重复问题", requestId));

        assertThat(response.runId()).isEqualTo(existing.id());
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        verifyNoInteractions(conversationLock, writeService);
    }

    @Test
    void shouldRejectConversationOwnedByAnotherUserAsNotFound() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findOwned(conversationId, 41L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.messages(
                conversationId,
                20))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void shouldRejectSecondConcurrentRun() {
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(conversationRepository.findOwned(conversationId, 41L))
                .thenReturn(Optional.of(conversation(conversationId)));
        when(runRepository.findByRequest(conversationId, requestId))
                .thenReturn(Optional.empty());
        when(conversationLock.tryAcquire(conversationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.submit(
                conversationId,
                new SendMessageRequest("并发问题", requestId)))
                .isInstanceOf(ConversationBusyException.class);
        verifyNoInteractions(writeService);
    }

    private Conversation conversation(UUID id) {
        return new Conversation(
                id,
                41L,
                "测试会话",
                "ACTIVE",
                Instant.now(),
                Instant.now());
    }
}
