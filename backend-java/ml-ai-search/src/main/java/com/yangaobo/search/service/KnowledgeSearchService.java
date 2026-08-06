package com.yangaobo.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.search.config.AiSearchProperties;
import com.yangaobo.search.dto.KeywordSearchRequest;
import com.yangaobo.search.dto.KeywordSearchResponse;
import com.yangaobo.search.dto.KnowledgeChunkUpsertRequest;
import com.yangaobo.search.dto.KnowledgeIndexResponse;
import com.yangaobo.search.es.KnowledgeChunkDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgeSearchService {

    private final ElasticsearchOperations operations;
    private final ObjectMapper objectMapper;
    private final AiSearchProperties properties;
    private final Map<String, Object> sourceLocks = new ConcurrentHashMap<>();

    public KnowledgeSearchService(
            ElasticsearchOperations operations,
            ObjectMapper objectMapper,
            AiSearchProperties properties) {
        this.operations = operations;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public KeywordSearchResponse search(KeywordSearchRequest request) {
        long started = System.nanoTime();
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", List.of(Map.of(
                "multi_match", Map.of(
                        "query", request.query().strip(),
                        "fields", List.of("title^3", "content"),
                        "type", "best_fields"))));
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("status", "ACTIVE")));
        if (request.sourceTypes() != null && !request.sourceTypes().isEmpty()) {
            List<String> sourceTypes = request.sourceTypes().stream()
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .toList();
            filters.add(Map.of("terms", Map.of("source_type", sourceTypes)));
        }
        bool.put("filter", filters);
        StringQuery query = new StringQuery(
                json(Map.of("bool", bool)),
                PageRequest.of(0, request.topK()));
        SearchHits<KnowledgeChunkDocument> results = operations.search(
                query,
                KnowledgeChunkDocument.class,
                readIndex());
        List<KeywordSearchResponse.Hit> hits = new ArrayList<>();
        int rank = 1;
        for (SearchHit<KnowledgeChunkDocument> result : results.getSearchHits()) {
            KnowledgeChunkDocument document = result.getContent();
            hits.add(new KeywordSearchResponse.Hit(
                    document.getChunkId(),
                    rank++,
                    result.getScore(),
                    document.getSourceType(),
                    document.getSourceId(),
                    document.getContentVersion()));
        }
        return new KeywordSearchResponse(
                properties.getIndexAlias(),
                (System.nanoTime() - started) / 1_000_000,
                hits,
                false);
    }

    public KnowledgeIndexResponse upsert(KnowledgeChunkUpsertRequest request) {
        String sourceType = request.sourceType().toUpperCase(Locale.ROOT);
        String lockKey = sourceType + ":" + request.sourceId();
        synchronized (sourceLocks.computeIfAbsent(lockKey, ignored -> new Object())) {
            SourceState current = currentState(sourceType, request.sourceId());
            if (current.version() > request.contentVersion()
                    || (current.version() == request.contentVersion()
                    && "DELETED".equals(current.status()))) {
                return new KnowledgeIndexResponse(
                        sourceType,
                        request.sourceId(),
                        request.contentVersion(),
                        "SKIPPED_OLD_VERSION",
                        0,
                        0);
            }
            long deleted = deleteSourceDocuments(sourceType, request.sourceId());
            List<KnowledgeChunkDocument> documents = request.chunks().stream()
                    .map(chunk -> toDocument(request, chunk, sourceType))
                    .toList();
            operations.save(documents, writeIndex());
            operations.indexOps(writeIndex()).refresh();
            return new KnowledgeIndexResponse(
                    sourceType,
                    request.sourceId(),
                    request.contentVersion(),
                    "INDEXED",
                    documents.size(),
                    deleted);
        }
    }

    public KnowledgeIndexResponse delete(String sourceTypeValue, String sourceId, long version) {
        String sourceType = sourceTypeValue.toUpperCase(Locale.ROOT);
        String lockKey = sourceType + ":" + sourceId;
        synchronized (sourceLocks.computeIfAbsent(lockKey, ignored -> new Object())) {
            SourceState current = currentState(sourceType, sourceId);
            if (current.version() > version) {
                return new KnowledgeIndexResponse(
                        sourceType, sourceId, version, "SKIPPED_OLD_VERSION", 0, 0);
            }
            long deleted = deleteSourceDocuments(sourceType, sourceId);
            operations.save(tombstone(sourceType, sourceId, version), writeIndex());
            operations.indexOps(writeIndex()).refresh();
            return new KnowledgeIndexResponse(
                    sourceType, sourceId, version, "DELETED", 0, deleted);
        }
    }

    private SourceState currentState(String sourceType, String sourceId) {
        StringQuery query = new StringQuery(
                sourceFilter(sourceType, sourceId),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "content_version")));
        SearchHit<KnowledgeChunkDocument> result = operations.searchOne(
                query,
                KnowledgeChunkDocument.class,
                readIndex());
        return result == null
                ? new SourceState(0, "MISSING")
                : new SourceState(
                        result.getContent().getContentVersion(),
                        result.getContent().getStatus());
    }

    private long deleteSourceDocuments(String sourceType, String sourceId) {
        StringQuery query = new StringQuery(sourceFilter(sourceType, sourceId));
        ByQueryResponse response = operations.delete(
                query,
                KnowledgeChunkDocument.class,
                writeIndex());
        return response.getDeleted();
    }

    private String sourceFilter(String sourceType, String sourceId) {
        return json(Map.of(
                "bool", Map.of(
                        "filter", List.of(
                                Map.of("term", Map.of("source_type", sourceType)),
                                Map.of("term", Map.of("source_id", sourceId))))));
    }

    private KnowledgeChunkDocument toDocument(
            KnowledgeChunkUpsertRequest request,
            KnowledgeChunkUpsertRequest.Chunk chunk,
            String sourceType) {
        KnowledgeChunkDocument document = new KnowledgeChunkDocument();
        document.setChunkId(chunk.chunkId());
        document.setSourceType(sourceType);
        document.setSourceId(request.sourceId());
        document.setTitle(chunk.title());
        document.setContent(chunk.content());
        document.setSourceUrl(chunk.sourceUrl());
        document.setContentVersion(request.contentVersion());
        document.setChunkIndex(chunk.chunkIndex());
        document.setContentHash(chunk.contentHash());
        document.setStatus("ACTIVE");
        return document;
    }

    private KnowledgeChunkDocument tombstone(String sourceType, String sourceId, long version) {
        KnowledgeChunkDocument document = new KnowledgeChunkDocument();
        document.setChunkId("__source__:" + sourceType + ":" + sourceId);
        document.setSourceType(sourceType);
        document.setSourceId(sourceId);
        document.setTitle("deleted source");
        document.setContent("deleted source");
        document.setSourceUrl("mylesson://deleted/" + sourceType.toLowerCase(Locale.ROOT)
                + "/" + sourceId);
        document.setContentVersion(version);
        document.setChunkIndex(-1);
        document.setContentHash("deleted");
        document.setStatus("DELETED");
        return document;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build Elasticsearch query", exception);
        }
    }

    private IndexCoordinates readIndex() {
        return IndexCoordinates.of(properties.getIndexAlias());
    }

    private IndexCoordinates writeIndex() {
        return IndexCoordinates.of(properties.getIndexAlias());
    }

    private record SourceState(long version, String status) {
    }
}
