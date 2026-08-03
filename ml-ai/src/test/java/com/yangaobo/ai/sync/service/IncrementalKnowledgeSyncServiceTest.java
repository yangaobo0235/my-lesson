package com.yangaobo.ai.sync.service;

import com.yangaobo.ai.knowledge.model.KnowledgeSourceKey;
import com.yangaobo.ai.knowledge.repository.KnowledgeSourceRepository;
import com.yangaobo.ai.knowledge.service.KnowledgeDocumentFactory;
import com.yangaobo.ai.knowledge.service.KnowledgeSourceIndexer;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.sync.model.KnowledgeChangeEvent;
import com.yangaobo.ai.sync.repository.KnowledgeInboxRepository;
import com.yangaobo.ai.observability.AiMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncrementalKnowledgeSyncServiceTest {

    private KnowledgeInboxRepository inbox;
    private KnowledgeSourceRepository sources;
    private KnowledgeSourceIndexer indexer;
    private AiBusinessGateway gateway;
    private IncrementalKnowledgeSyncService service;

    @BeforeEach
    void setUp() {
        inbox = mock(KnowledgeInboxRepository.class);
        sources = mock(KnowledgeSourceRepository.class);
        indexer = mock(KnowledgeSourceIndexer.class);
        gateway = mock(AiBusinessGateway.class);
        service = new IncrementalKnowledgeSyncService(
                inbox,
                sources,
                indexer,
                mock(KnowledgeDocumentFactory.class),
                gateway,
                mock(AiMetrics.class));
    }

    @Test
    void shouldAcknowledgeDuplicateEventWithoutDoingWork() {
        KnowledgeChangeEvent event = event("COURSE_UPDATED", 7L);
        when(inbox.start(event)).thenReturn(false);

        service.handle(event);

        verify(gateway, never()).getCourse(18L);
        verify(inbox, never()).succeeded(event, false);
    }

    @Test
    void shouldSkipAnOlderCourseEvent() {
        KnowledgeChangeEvent event = event("COURSE_UPDATED", 7L);
        when(inbox.start(event)).thenReturn(true);
        when(sources.find(KnowledgeDocumentFactory.COURSE, "18"))
                .thenReturn(Optional.of(
                        new KnowledgeSourceRepository.SourceState(
                                "hash",
                                8L,
                                "ACTIVE")));

        service.handle(event);

        verify(gateway, never()).getCourse(18L);
        verify(inbox).succeeded(event, true);
    }

    @Test
    void shouldDeleteBothCourseSources() {
        KnowledgeChangeEvent event = event("COURSE_DELETED", 9L);
        when(inbox.start(event)).thenReturn(true);
        when(indexer.delete(
                new KnowledgeSourceKey(KnowledgeDocumentFactory.COURSE, "18"),
                9L)).thenReturn(true);

        service.handle(event);

        verify(indexer).delete(
                new KnowledgeSourceKey(KnowledgeDocumentFactory.COURSE, "18"),
                9L);
        verify(indexer).delete(
                new KnowledgeSourceKey(
                        KnowledgeDocumentFactory.COURSE_EPISODES,
                        "18"),
                9L);
        verify(inbox).succeeded(event, false);
    }

    private KnowledgeChangeEvent event(String type, long version) {
        return new KnowledgeChangeEvent(
                UUID.randomUUID(),
                type,
                "18",
                version,
                Instant.now());
    }
}
