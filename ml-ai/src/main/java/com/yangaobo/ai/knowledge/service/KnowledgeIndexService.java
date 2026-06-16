package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.SaleAiClient;
import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import com.yangaobo.ai.knowledge.model.KnowledgeIndexStatus;
import com.yangaobo.ai.knowledge.model.KnowledgeSourceKey;
import com.yangaobo.ai.knowledge.repository.KnowledgeSourceRepository;
import com.yangaobo.ai.knowledge.service.KnowledgeIndexStatusStore.RebuildCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class KnowledgeIndexService {

    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeIndexService.class);
    private static final int PAGE_SIZE = 100;
    private static final Set<String> MANAGED_SOURCE_TYPES = Set.of(
            KnowledgeDocumentFactory.COURSE,
            KnowledgeDocumentFactory.COURSE_EPISODES,
            KnowledgeDocumentFactory.ARTICLE,
            KnowledgeDocumentFactory.NOTICE);

    private final CourseAiClient courseClient;
    private final SaleAiClient saleClient;
    private final KnowledgeDocumentFactory documentFactory;
    private final KnowledgeSourceIndexer sourceIndexer;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeRebuildLock rebuildLock;
    private final KnowledgeIndexStatusStore statusStore;
    private final TaskExecutor taskExecutor;

    public KnowledgeIndexService(
            CourseAiClient courseClient,
            SaleAiClient saleClient,
            KnowledgeDocumentFactory documentFactory,
            KnowledgeSourceIndexer sourceIndexer,
            KnowledgeSourceRepository sourceRepository,
            KnowledgeRebuildLock rebuildLock,
            KnowledgeIndexStatusStore statusStore,
            @Qualifier("knowledgeIndexExecutor") TaskExecutor taskExecutor) {
        this.courseClient = courseClient;
        this.saleClient = saleClient;
        this.documentFactory = documentFactory;
        this.sourceIndexer = sourceIndexer;
        this.sourceRepository = sourceRepository;
        this.rebuildLock = rebuildLock;
        this.statusStore = statusStore;
        this.taskExecutor = taskExecutor;
    }

    public boolean startRebuild() {
        Optional<String> lockToken = rebuildLock.tryAcquire();
        if (lockToken.isEmpty()) {
            return false;
        }
        try {
            statusStore.running();
            taskExecutor.execute(() -> rebuildAll(lockToken.get()));
            return true;
        } catch (RuntimeException exception) {
            rebuildLock.release(lockToken.get());
            statusStore.failed(
                    new RebuildCounters(0, 0, 1, 0),
                    "Unable to start knowledge rebuild");
            throw exception;
        }
    }

    public KnowledgeIndexStatus status() {
        return statusStore.get();
    }

    private void rebuildAll(String lockToken) {
        MutableCounters counters = new MutableCounters();
        Set<KnowledgeSourceKey> seenSources = new HashSet<>();
        try {
            indexCourses(seenSources, counters);
            indexArticles(seenSources, counters);
            indexNotices(seenSources, counters);
            removeStaleSources(seenSources, counters);
            statusStore.finished(counters.snapshot());
        } catch (RuntimeException exception) {
            log.error(
                    "Knowledge rebuild failed with {}",
                    exception.getClass().getSimpleName(),
                    exception);
            counters.failedSources++;
            statusStore.failed(
                    counters.snapshot(),
                    "Knowledge rebuild failed: "
                            + exception.getClass().getSimpleName());
        } finally {
            rebuildLock.release(lockToken);
        }
    }

    private void indexCourses(
            Set<KnowledgeSourceKey> seenSources,
            MutableCounters counters) {
        long cursor = 0L;
        while (true) {
            CourseAiClient.CursorPage<CourseAiClient.CourseKnowledge> page =
                    requireData(courseClient.knowledge(cursor, PAGE_SIZE), "course");
            for (CourseAiClient.CourseKnowledge course : safeItems(page.items())) {
                for (KnowledgeDocument source : documentFactory.fromCourse(course)) {
                    indexSource(source, seenSources, counters);
                }
            }
            if (!page.hasMore()) {
                return;
            }
            cursor = nextCursor(cursor, page.nextCursor(), "course");
        }
    }

    private void indexArticles(
            Set<KnowledgeSourceKey> seenSources,
            MutableCounters counters) {
        long cursor = 0L;
        while (true) {
            SaleAiClient.CursorPage<SaleAiClient.ArticleKnowledge> page =
                    requireData(
                            saleClient.articleKnowledge(cursor, PAGE_SIZE),
                            "article");
            for (SaleAiClient.ArticleKnowledge article : safeItems(page.items())) {
                indexSource(
                        documentFactory.fromArticle(article),
                        seenSources,
                        counters);
            }
            if (!page.hasMore()) {
                return;
            }
            cursor = nextCursor(cursor, page.nextCursor(), "article");
        }
    }

    private void indexNotices(
            Set<KnowledgeSourceKey> seenSources,
            MutableCounters counters) {
        long cursor = 0L;
        while (true) {
            SaleAiClient.CursorPage<SaleAiClient.NoticeKnowledge> page =
                    requireData(
                            saleClient.noticeKnowledge(cursor, PAGE_SIZE),
                            "notice");
            for (SaleAiClient.NoticeKnowledge notice : safeItems(page.items())) {
                indexSource(
                        documentFactory.fromNotice(notice),
                        seenSources,
                        counters);
            }
            if (!page.hasMore()) {
                return;
            }
            cursor = nextCursor(cursor, page.nextCursor(), "notice");
        }
    }

    private void indexSource(
            KnowledgeDocument source,
            Set<KnowledgeSourceKey> seenSources,
            MutableCounters counters) {
        KnowledgeSourceKey key =
                new KnowledgeSourceKey(source.sourceType(), source.sourceId());
        seenSources.add(key);
        try {
            KnowledgeSourceIndexer.IndexOutcome outcome =
                    sourceIndexer.index(source);
            if (outcome.indexed()) {
                counters.indexedSources++;
                counters.indexedChunks += outcome.chunkCount();
            } else {
                counters.skippedSources++;
            }
        } catch (RuntimeException exception) {
            counters.failedSources++;
            log.warn(
                    "Failed to index knowledge source {}/{}: {}: {}",
                    source.sourceType(),
                    source.sourceId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
        }
    }

    private void removeStaleSources(
            Set<KnowledgeSourceKey> seenSources,
            MutableCounters counters) {
        for (KnowledgeSourceKey stored : sourceRepository.findActiveKeys()) {
            if (!MANAGED_SOURCE_TYPES.contains(stored.sourceType())
                    || seenSources.contains(stored)) {
                continue;
            }
            try {
                sourceIndexer.delete(stored);
            } catch (RuntimeException exception) {
                counters.failedSources++;
                log.warn(
                        "Failed to remove stale knowledge source {}/{}: {}",
                        stored.sourceType(),
                        stored.sourceId(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    private <T> T requireData(
            com.yangaobo.ai.client.InternalAiResponse<T> response,
            String sourceName) {
        if (response == null || !response.successful() || response.data() == null) {
            throw new IllegalStateException(
                    "Unable to load " + sourceName + " knowledge");
        }
        return response.data();
    }

    private <T> List<T> safeItems(List<T> items) {
        return items == null ? List.of() : items;
    }

    private long nextCursor(long current, Long next, String sourceName) {
        if (next == null || next <= current) {
            throw new IllegalStateException(
                    "Invalid " + sourceName + " knowledge cursor");
        }
        return next;
    }

    private static final class MutableCounters {

        private long indexedSources;
        private long skippedSources;
        private long failedSources;
        private long indexedChunks;

        private RebuildCounters snapshot() {
            return new RebuildCounters(
                    indexedSources,
                    skippedSources,
                    failedSources,
                    indexedChunks);
        }
    }
}
