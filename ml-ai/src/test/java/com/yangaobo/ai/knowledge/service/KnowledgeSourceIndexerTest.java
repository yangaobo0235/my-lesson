package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.knowledge.model.KnowledgeChunk;
import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import com.yangaobo.ai.knowledge.repository.KnowledgeSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeSourceIndexerTest {

    private VectorStore vectorStore;
    private KnowledgeSourceRepository sourceRepository;
    private DeterministicKnowledgeChunker chunker;
    private KnowledgeSourceIndexer indexer;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        sourceRepository = mock(KnowledgeSourceRepository.class);
        chunker = mock(DeterministicKnowledgeChunker.class);
        indexer = new KnowledgeSourceIndexer(
                vectorStore,
                sourceRepository,
                chunker,
                "text-embedding-v4",
                "v1");
    }

    @Test
    void shouldSkipUnchangedSourceWithCompleteChunks() {
        KnowledgeDocument source = source();
        when(chunker.chunk(source))
                .thenReturn(List.of(new KnowledgeChunk(0, "chunk")));
        when(sourceRepository.find("COURSE", "1"))
                .thenReturn(Optional.of(
                        new KnowledgeSourceRepository.SourceState(
                                "content-hash",
                                1L,
                                "ACTIVE")));
        when(sourceRepository.countChunks(
                "COURSE",
                "1",
                "text-embedding-v4",
                "v1")).thenReturn(1L);

        KnowledgeSourceIndexer.IndexOutcome outcome = indexer.index(source);

        assertThat(outcome.indexed()).isFalse();
        verify(vectorStore, never()).add(anyList());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
        verify(sourceRepository, never()).upsert(any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReplaceVectorsAndWriteRequiredMetadata() {
        KnowledgeDocument source = source();
        when(chunker.chunk(source)).thenReturn(List.of(
                new KnowledgeChunk(0, "first"),
                new KnowledgeChunk(1, "second")));
        when(sourceRepository.find("COURSE", "1"))
                .thenReturn(Optional.empty());

        KnowledgeSourceIndexer.IndexOutcome outcome = indexer.index(source);

        assertThat(outcome.indexed()).isTrue();
        assertThat(outcome.chunkCount()).isEqualTo(2);
        verify(vectorStore).delete(any(Filter.Expression.class));
        ArgumentCaptor<List> documentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        verify(sourceRepository).upsert(source);

        List<Document> documents = documentsCaptor.getValue();
        assertThat(documents).hasSize(2);
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.getMetadata())
                    .containsEntry("source_type", "COURSE")
                    .containsEntry("source_id", "1")
                    .containsEntry("title", "测试课程")
                    .containsEntry("source_url", "http://localhost/course/1")
                    .containsEntry("version", 1L)
                    .containsEntry("content_hash", "content-hash")
                    .containsEntry("embedding_model", "text-embedding-v4")
                    .containsEntry("embedding_version", "v1")
                    .containsKey("chunk_index");
        });
    }

    @Test
    void shouldReindexWhenEmbeddingMetadataDoesNotMatch() {
        KnowledgeDocument source = source();
        when(chunker.chunk(source))
                .thenReturn(List.of(new KnowledgeChunk(0, "chunk")));
        when(sourceRepository.find("COURSE", "1"))
                .thenReturn(Optional.of(
                        new KnowledgeSourceRepository.SourceState(
                                "content-hash",
                                1L,
                                "ACTIVE")));
        when(sourceRepository.countChunks(
                "COURSE",
                "1",
                "text-embedding-v4",
                "v1")).thenReturn(0L);

        KnowledgeSourceIndexer.IndexOutcome outcome = indexer.index(source);

        assertThat(outcome.indexed()).isTrue();
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(vectorStore).add(anyList());
        verify(sourceRepository).upsert(source);
    }

    @Test
    void shouldIgnoreAnOlderSourceVersion() {
        KnowledgeDocument source = source();
        when(chunker.chunk(source))
                .thenReturn(List.of(new KnowledgeChunk(0, "chunk")));
        when(sourceRepository.find("COURSE", "1"))
                .thenReturn(Optional.of(
                        new KnowledgeSourceRepository.SourceState(
                                "newer-hash",
                                2L,
                                "ACTIVE")));

        KnowledgeSourceIndexer.IndexOutcome outcome = indexer.index(source);

        assertThat(outcome.indexed()).isFalse();
        assertThat(outcome.chunkCount()).isZero();
        verify(vectorStore, never()).add(anyList());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
        verify(sourceRepository, never()).upsert(any());
    }

    private KnowledgeDocument source() {
        return new KnowledgeDocument(
                "COURSE",
                "1",
                "测试课程",
                "课程正文",
                "http://localhost/course/1",
                1L,
                "content-hash",
                Map.of("author", "测试作者"));
    }
}
