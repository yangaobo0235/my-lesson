package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.agent.model.IntentDecision;
import com.yangaobo.ai.agent.service.IntentClassifier;
import com.yangaobo.ai.conversation.config.ConversationProperties;
import com.yangaobo.ai.conversation.dto.MessageSubmissionResponse;
import com.yangaobo.ai.conversation.dto.SendMessageRequest;
import com.yangaobo.ai.conversation.exception.ConversationBusyException;
import com.yangaobo.ai.conversation.exception.ConversationNotFoundException;
import com.yangaobo.ai.conversation.model.Conversation;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.conversation.model.ConversationMemoryContext;
import com.yangaobo.ai.conversation.model.ConversationMessage;
import com.yangaobo.ai.conversation.model.ConversationRun;
import com.yangaobo.ai.conversation.repository.ConversationMessageRepository;
import com.yangaobo.ai.conversation.repository.ConversationRepository;
import com.yangaobo.ai.conversation.repository.ConversationRunRepository;
import com.yangaobo.ai.rag.model.AiAnswer;
import com.yangaobo.ai.rag.model.HybridSearchResult;
import com.yangaobo.ai.rag.service.KnowledgeAnswerService;
import com.yangaobo.ai.observability.AiLogContext;
import com.yangaobo.ai.observability.AiMetrics;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.tool.model.ToolRunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationService {

    private static final Logger log =
            LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ConversationRunRepository runRepository;
    private final ConversationWriteService writeService;
    private final ConversationMemoryService memoryService;
    private final ConversationLock conversationLock;
    private final ConversationEventPublisher eventPublisher;
    private final KnowledgeAnswerService answerService;
    private final IntentClassifier intentClassifier;
    private final ConversationProperties properties;
    private final TaskExecutor taskExecutor;
    private final AiMetrics aiMetrics;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMessageRepository messageRepository,
            ConversationRunRepository runRepository,
            ConversationWriteService writeService,
            ConversationMemoryService memoryService,
            ConversationLock conversationLock,
            ConversationEventPublisher eventPublisher,
            KnowledgeAnswerService answerService,
            IntentClassifier intentClassifier,
            ConversationProperties properties,
            AiMetrics aiMetrics,
            @Qualifier("conversationExecutor") TaskExecutor taskExecutor) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.writeService = writeService;
        this.memoryService = memoryService;
        this.conversationLock = conversationLock;
        this.eventPublisher = eventPublisher;
        this.answerService = answerService;
        this.intentClassifier = intentClassifier;
        this.properties = properties;
        this.aiMetrics = aiMetrics;
        this.taskExecutor = taskExecutor;
    }

    public Conversation create(String requestedTitle) {
        AuthenticatedUser user = UserContext.requireUser();
        String title = requestedTitle == null || requestedTitle.isBlank()
                ? "新对话"
                : requestedTitle.trim();
        return conversationRepository.create(user.id(), title);
    }

    public List<Conversation> list() {
        return conversationRepository.findByUser(
                UserContext.requireUser().id());
    }

    public List<ConversationMessage> messages(
            UUID conversationId,
            Integer requestedLimit) {
        AuthenticatedUser user = UserContext.requireUser();
        requireOwned(conversationId, user.id());
        int limit = requestedLimit == null
                ? properties.getMessageListLimit()
                : Math.max(1, Math.min(200, requestedLimit));
        return messageRepository.findVisible(conversationId, limit);
    }

    public SseEmitter openStream(UUID conversationId) {
        AuthenticatedUser user = UserContext.requireUser();
        requireOwned(conversationId, user.id());
        return eventPublisher.open(user.id(), conversationId);
    }

    public MessageSubmissionResponse submit(
            UUID conversationId,
            SendMessageRequest request) {
        AuthenticatedUser user = UserContext.requireUser();
        requireOwned(conversationId, user.id());

        Optional<ConversationRun> existing =
                runRepository.findByRequest(
                        conversationId,
                        request.requestId());
        if (existing.isPresent()) {
            return response(existing.get());
        }

        ConversationLock.LockLease lease = conversationLock
                .tryAcquire(conversationId)
                .orElseThrow(ConversationBusyException::new);
        String traceId = UUID.randomUUID().toString();
        ConversationWriteService.Submission submission;
        try {
            submission = writeService.createSubmission(
                    conversationId,
                    user.id(),
                    request.message().trim(),
                    request.requestId(),
                    traceId);
        } catch (DataIntegrityViolationException exception) {
            conversationLock.release(lease);
            return runRepository.findByRequest(
                            conversationId,
                            request.requestId())
                    .map(this::response)
                    .orElseThrow(() -> exception);
        } catch (RuntimeException exception) {
            conversationLock.release(lease);
            throw exception;
        }

        try {
            taskExecutor.execute(() -> execute(
                    user,
                    submission,
                    request.message().trim(),
                    lease));
        } catch (RuntimeException exception) {
            conversationLock.release(lease);
            runRepository.markFailed(
                    submission.run().id(),
                    "Unable to schedule conversation run",
                    0L);
            throw exception;
        }
        return response(submission.run());
    }

    public void delete(UUID conversationId) {
        AuthenticatedUser user = UserContext.requireUser();
        requireOwned(conversationId, user.id());
        ConversationLock.LockLease lease = conversationLock
                .tryAcquire(conversationId)
                .orElseThrow(ConversationBusyException::new);
        try {
            if (!conversationRepository.softDelete(
                    conversationId,
                    user.id())) {
                throw new ConversationNotFoundException();
            }
        } finally {
            conversationLock.release(lease);
        }
    }

    private void execute(
            AuthenticatedUser user,
            ConversationWriteService.Submission submission,
            String question,
            ConversationLock.LockLease lease) {
        ConversationRun run = submission.run();
        Instant startedAt = Instant.now();
        AiLogContext.open(user.id(), run);
        try {
            runRepository.markRunning(run.id());
            publish(user.id(), run, ConversationEventType.RUN_STARTED, Map.of(
                    "status", "RUNNING"));

            ConversationMemoryContext memory = memoryService.prepare(
                    run.conversationId(),
                    submission.userMessage().id(),
                    run.traceId());
            IntentDecision intentDecision = intentClassifier.classify(
                    question,
                    memory.promptContext());
            runRepository.updateIntent(
                    run.id(),
                    intentDecision.intent().name());
            publish(
                    user.id(),
                    run,
                    ConversationEventType.INTENT_DETECTED,
                    Map.of(
                            "intent", intentDecision.intent().name(),
                            "confidence", intentDecision.confidence(),
                            "reason", intentDecision.reason()));
            ToolRunContext toolRunContext = new ToolRunContext(
                    user,
                    run,
                    (type, data) -> publish(
                            user.id(),
                            run,
                            type,
                            data),
                    route -> runRepository.updateRoute(
                            run.id(),
                            route.profileName(),
                            route.profileVersion(),
                            route.intent(),
                            route.confidence(),
                            route.conservative()));
            publish(
                    user.id(),
                    run,
                    ConversationEventType.RETRIEVAL_STARTED,
                    Map.of());
            AiAnswer answer = answerService.answer(
                    question,
                    memory.retrievalQuery(question),
                    memory.promptContext(),
                    result -> publishRetrievalCompleted(
                            user.id(),
                            run,
                            result),
                    toolRunContext,
                    intentDecision);
            runRepository.updateToolCallCount(
                    run.id(), toolRunContext.toolCallCount());
            long latencyMillis = Duration
                    .between(startedAt, Instant.now())
                    .toMillis();
            ConversationMessage assistantMessage =
                    writeService.complete(run, answer, latencyMillis);

            for (String delta : split(answer.answer())) {
                publish(
                        user.id(),
                        run,
                        ConversationEventType.ANSWER_DELTA,
                        Map.of("delta", delta));
            }
            answer.citations().forEach(citation -> publish(
                    user.id(),
                    run,
                    ConversationEventType.CITATION,
                    Map.of("citation", citation)));
            publish(
                    user.id(),
                    run,
                    ConversationEventType.RUN_COMPLETED,
                    Map.of(
                            "status", "SUCCEEDED",
                            "assistantMessageId", assistantMessage.id(),
                            "latencyMillis", latencyMillis));
        } catch (RuntimeException exception) {
            long latencyMillis = Duration
                    .between(startedAt, Instant.now())
                    .toMillis();
            log.error(
                    "Conversation run {} failed",
                    run.id(),
                    exception);
            runRepository.markFailed(
                    run.id(),
                    exception.getClass().getSimpleName(),
                    latencyMillis);
            publish(
                    user.id(),
                    run,
                    ConversationEventType.RUN_FAILED,
                    Map.of(
                            "status", "FAILED",
                            "code", "AI_CONVERSATION_FAILED",
                            "message", "对话处理失败，请稍后重试",
                            "retryable", true));
        } finally {
            aiMetrics.request(Duration.between(startedAt, Instant.now()));
            conversationLock.release(lease);
            AiLogContext.close();
        }
    }

    private void publishRetrievalCompleted(
            Long userId,
            ConversationRun run,
            HybridSearchResult result) {
        publish(
                userId,
                run,
                ConversationEventType.RETRIEVAL_COMPLETED,
                Map.of(
                        "hitCount", result.hits().size(),
                        "reranked", result.reranked()));
    }

    private void publish(
            Long userId,
            ConversationRun run,
            ConversationEventType type,
            Map<String, Object> data) {
        eventPublisher.publish(
                userId,
                run.conversationId(),
                type,
                run.id(),
                run.requestId(),
                run.traceId(),
                data);
    }

    private List<String> split(String answer) {
        if (answer == null || answer.isEmpty()) {
            return List.of();
        }
        int chunkSize = Math.max(1, properties.getAnswerDeltaSize());
        List<String> chunks = new java.util.ArrayList<>();
        int offset = 0;
        while (offset < answer.length()) {
            int remainingCodePoints =
                    answer.codePointCount(offset, answer.length());
            int count = Math.min(chunkSize, remainingCodePoints);
            int end = answer.offsetByCodePoints(offset, count);
            chunks.add(answer.substring(offset, end));
            offset = end;
        }
        return List.copyOf(chunks);
    }

    private Conversation requireOwned(UUID conversationId, Long userId) {
        return conversationRepository.findOwned(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);
    }

    private MessageSubmissionResponse response(ConversationRun run) {
        return new MessageSubmissionResponse(
                run.conversationId(),
                run.id(),
                run.requestId(),
                run.status(),
                run.userMessageId(),
                run.assistantMessageId(),
                run.traceId());
    }
}
