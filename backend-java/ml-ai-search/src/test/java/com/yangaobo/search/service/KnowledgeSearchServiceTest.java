package com.yangaobo.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.search.config.AiSearchProperties;
import com.yangaobo.search.dto.KeywordSearchRequest;
import com.yangaobo.search.dto.KeywordSearchResponse;
import com.yangaobo.search.dto.KnowledgeChunkUpsertRequest;
import com.yangaobo.search.dto.KnowledgeIndexResponse;
import com.yangaobo.search.es.KnowledgeChunkDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSearchServiceTest {

    @Mock
    private ElasticsearchOperations operations;

    private KnowledgeSearchService service;

    @BeforeEach
    void setUp() {
        AiSearchProperties properties = new AiSearchProperties();
        service = new KnowledgeSearchService(operations, new ObjectMapper(), properties);
    }

    @Test
    void searchReturnsRankedChunkMetadata() {
        KnowledgeChunkDocument document = document(3);
        @SuppressWarnings("unchecked")
        SearchHits<KnowledgeChunkDocument> searchHits = mock(SearchHits.class);
        SearchHit<KnowledgeChunkDocument> searchHit = mock(SearchHit.class);
        when(searchHit.getContent()).thenReturn(document);
        when(searchHit.getScore()).thenReturn(8.4f);
        when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));
        when(operations.search(
                any(Query.class),
                eq(KnowledgeChunkDocument.class),
                any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        KeywordSearchResponse response = service.search(
                new KeywordSearchRequest("Java 泛型", 20, List.of("COURSE")));

        assertEquals(1, response.hits().size());
        assertEquals(1, response.hits().get(0).rank());
        assertEquals(3, response.hits().get(0).contentVersion());
    }

    @Test
    void upsertSkipsAnOlderSourceVersion() {
        SearchHit<KnowledgeChunkDocument> current = mock(SearchHit.class);
        when(current.getContent()).thenReturn(document(5));
        when(operations.searchOne(
                any(Query.class),
                eq(KnowledgeChunkDocument.class),
                any(IndexCoordinates.class)))
                .thenReturn(current);

        KnowledgeIndexResponse response = service.upsert(request(4));

        assertEquals("SKIPPED_OLD_VERSION", response.status());
        verify(operations, never()).delete(
                any(Query.class),
                eq(KnowledgeChunkDocument.class),
                any(IndexCoordinates.class));
    }

    @Test
    void upsertReplacesSourceDocumentsAsOneBulk() {
        when(operations.searchOne(
                any(Query.class),
                eq(KnowledgeChunkDocument.class),
                any(IndexCoordinates.class)))
                .thenReturn(null);
        ByQueryResponse deleteResponse = mock(ByQueryResponse.class);
        when(deleteResponse.getDeleted()).thenReturn(2L);
        when(operations.delete(
                any(Query.class),
                eq(KnowledgeChunkDocument.class),
                any(IndexCoordinates.class)))
                .thenReturn(deleteResponse);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);

        KnowledgeIndexResponse response = service.upsert(request(4));

        assertEquals("INDEXED", response.status());
        assertEquals(1, response.indexedChunks());
        assertEquals(2, response.deletedChunks());
        verify(operations).save(any(Iterable.class), any(IndexCoordinates.class));
        verify(indexOperations).refresh();
    }

    private static KnowledgeChunkUpsertRequest request(long version) {
        return new KnowledgeChunkUpsertRequest(
                UUID.randomUUID(),
                "COURSE",
                "1",
                version,
                List.of(new KnowledgeChunkUpsertRequest.Chunk(
                        "00000000-0000-0000-0000-000000000001",
                        0,
                        "Java 泛型",
                        "Java 泛型用于类型安全。",
                        "mylesson://course/1",
                        "hash")));
    }

    private static KnowledgeChunkDocument document(long version) {
        KnowledgeChunkDocument document = new KnowledgeChunkDocument();
        document.setChunkId("00000000-0000-0000-0000-000000000001");
        document.setSourceType("COURSE");
        document.setSourceId("1");
        document.setTitle("Java 泛型");
        document.setContent("Java 泛型用于类型安全。");
        document.setSourceUrl("mylesson://course/1");
        document.setContentVersion(version);
        document.setChunkIndex(0);
        document.setContentHash("hash");
        document.setStatus("ACTIVE");
        return document;
    }
}
