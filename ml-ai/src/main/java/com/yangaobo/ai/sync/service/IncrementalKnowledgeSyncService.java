package com.yangaobo.ai.sync.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.SaleAiClient;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import com.yangaobo.ai.knowledge.model.KnowledgeSourceKey;
import com.yangaobo.ai.knowledge.repository.KnowledgeSourceRepository;
import com.yangaobo.ai.knowledge.service.KnowledgeDocumentFactory;
import com.yangaobo.ai.knowledge.service.KnowledgeSourceIndexer;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.sync.model.KnowledgeChangeEvent;
import com.yangaobo.ai.sync.repository.KnowledgeInboxRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IncrementalKnowledgeSyncService {

    private final KnowledgeInboxRepository inboxRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeSourceIndexer sourceIndexer;
    private final KnowledgeDocumentFactory documentFactory;
    private final AiBusinessGateway businessGateway;

    public IncrementalKnowledgeSyncService(
            KnowledgeInboxRepository inboxRepository,
            KnowledgeSourceRepository sourceRepository,
            KnowledgeSourceIndexer sourceIndexer,
            KnowledgeDocumentFactory documentFactory,
            AiBusinessGateway businessGateway) {
        this.inboxRepository = inboxRepository;
        this.sourceRepository = sourceRepository;
        this.sourceIndexer = sourceIndexer;
        this.documentFactory = documentFactory;
        this.businessGateway = businessGateway;
    }

    public void handle(KnowledgeChangeEvent event) {
        validate(event);
        if (!inboxRepository.start(event)) {
            return;
        }
        try {
            boolean changed = switch (event.eventType()) {
                case "COURSE_CREATED", "COURSE_UPDATED" -> syncCourse(event);
                case "COURSE_DELETED" -> deleteCourse(event);
                case "ARTICLE_UPDATED" -> syncArticle(event);
                case "NOTICE_UPDATED" -> syncNotice(event);
                default -> throw new IllegalArgumentException(
                        "Unsupported knowledge event type: " + event.eventType());
            };
            inboxRepository.succeeded(event, !changed);
        } catch (RuntimeException exception) {
            inboxRepository.failed(event, exception);
            throw exception;
        }
    }

    private boolean syncCourse(KnowledgeChangeEvent event) {
        if (newerOrEqual(KnowledgeDocumentFactory.COURSE, event)) {
            return false;
        }
        try {
            CourseAiClient.CourseKnowledge course =
                    businessGateway.getCourse(Long.valueOf(event.aggregateId()));
            boolean changed = false;
            List<KnowledgeDocument> documents = documentFactory.fromCourse(course);
            for (KnowledgeDocument document : documents) {
                changed |= sourceIndexer.index(
                        withEventVersion(document, event.version())).indexed();
            }
            boolean hasEpisodes = documents.stream()
                    .anyMatch(document -> KnowledgeDocumentFactory.COURSE_EPISODES
                            .equals(document.sourceType()));
            if (!hasEpisodes) {
                changed |= sourceIndexer.delete(
                        key(KnowledgeDocumentFactory.COURSE_EPISODES, event),
                        event.version());
            }
            return changed;
        } catch (BusinessOperationException notFound) {
            return deleteCourse(event);
        }
    }

    private boolean deleteCourse(KnowledgeChangeEvent event) {
        boolean course = sourceIndexer.delete(
                key(KnowledgeDocumentFactory.COURSE, event),
                event.version());
        boolean episodes = sourceIndexer.delete(
                key(KnowledgeDocumentFactory.COURSE_EPISODES, event),
                event.version());
        return course || episodes;
    }

    private boolean syncArticle(KnowledgeChangeEvent event) {
        if (newerOrEqual(KnowledgeDocumentFactory.ARTICLE, event)) {
            return false;
        }
        try {
            SaleAiClient.ArticleKnowledge article =
                    businessGateway.getArticle(Long.valueOf(event.aggregateId()));
            return sourceIndexer.index(withEventVersion(
                    documentFactory.fromArticle(article),
                    event.version())).indexed();
        } catch (BusinessOperationException notFound) {
            return sourceIndexer.delete(
                    key(KnowledgeDocumentFactory.ARTICLE, event),
                    event.version());
        }
    }

    private boolean syncNotice(KnowledgeChangeEvent event) {
        if (newerOrEqual(KnowledgeDocumentFactory.NOTICE, event)) {
            return false;
        }
        try {
            SaleAiClient.NoticeKnowledge notice =
                    businessGateway.getNotice(Long.valueOf(event.aggregateId()));
            return sourceIndexer.index(withEventVersion(
                    documentFactory.fromNotice(notice),
                    event.version())).indexed();
        } catch (BusinessOperationException notFound) {
            return sourceIndexer.delete(
                    key(KnowledgeDocumentFactory.NOTICE, event),
                    event.version());
        }
    }

    private boolean newerOrEqual(String sourceType, KnowledgeChangeEvent event) {
        return sourceRepository.find(sourceType, event.aggregateId())
                .filter(state -> state.version() >= event.version())
                .isPresent();
    }

    private KnowledgeSourceKey key(String sourceType, KnowledgeChangeEvent event) {
        return new KnowledgeSourceKey(sourceType, event.aggregateId());
    }

    private KnowledgeDocument withEventVersion(
            KnowledgeDocument document,
            long eventVersion) {
        return new KnowledgeDocument(
                document.sourceType(),
                document.sourceId(),
                document.title(),
                document.content(),
                document.sourceUrl(),
                Math.max(document.version(), eventVersion),
                document.contentHash(),
                document.metadata());
    }

    private void validate(KnowledgeChangeEvent event) {
        if (event == null || event.eventId() == null
                || event.eventType() == null || event.eventType().isBlank()
                || event.aggregateId() == null || event.aggregateId().isBlank()
                || event.version() < 1) {
            throw new IllegalArgumentException("Invalid knowledge change event");
        }
    }
}
