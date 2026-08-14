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
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class KnowledgeSearchService {

    private final ElasticsearchOperations operations;
    private final ObjectMapper objectMapper;
    private final AiSearchProperties properties;

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
        if (!claimSourceVersion(
                sourceType, request.sourceId(), request.contentVersion(), "ACTIVE")) {
            return new KnowledgeIndexResponse(
                    sourceType,
                    request.sourceId(),
                    request.contentVersion(),
                    "SKIPPED_OLD_VERSION",
                    0,
                    0);
        }
        long deleted = deleteOlderSourceDocuments(
                sourceType, request.sourceId(), request.contentVersion(), false);
        List<KnowledgeChunkDocument> documents = request.chunks().stream()
                .map(chunk -> toDocument(request, chunk, sourceType))
                .toList();
        bulkIndexWithExternalVersion(documents, request.contentVersion());
        operations.indexOps(writeIndex()).refresh();
        return new KnowledgeIndexResponse(
                sourceType,
                request.sourceId(),
                request.contentVersion(),
                "INDEXED",
                documents.size(),
                deleted);
    }

    public KnowledgeIndexResponse delete(String sourceTypeValue, String sourceId, long version) {
        String sourceType = sourceTypeValue.toUpperCase(Locale.ROOT);
        if (!claimSourceVersion(sourceType, sourceId, version, "DELETED")) {
            return new KnowledgeIndexResponse(
                    sourceType, sourceId, version, "SKIPPED_OLD_VERSION", 0, 0);
        }
        long deleted = deleteOlderSourceDocuments(sourceType, sourceId, version, true);
        operations.indexOps(writeIndex()).refresh();
        return new KnowledgeIndexResponse(
                sourceType, sourceId, version, "DELETED", 0, deleted);
    }

    private boolean claimSourceVersion(
            String sourceType, String sourceId, long version, String status) {
        Map<String, Object> state = sourceState(sourceType, sourceId, version, status);
        UpdateQuery query = UpdateQuery.builder(sourceStateId(sourceType, sourceId))
                .withScript(
                        "if (ctx._source.content_version > params.state.content_version "
                                + "|| (ctx._source.content_version == params.state.content_version "
                                + "&& ctx._source.status == 'DELETED')) { ctx.op = 'none'; } "
                                + "else { ctx._source = params.state; }")
                .withLang("painless")
                .withParams(Map.of("state", state))
                .withUpsert(Document.from(state))
                .withScriptedUpsert(true)
                .withScriptType(ScriptType.INLINE)
                .withRetryOnConflict(5)
                .build();
        UpdateResponse response = operations.update(query, writeIndex());
        return response.getResult() != UpdateResponse.Result.NOOP;
    }

    private long deleteOlderSourceDocuments(
            String sourceType, String sourceId, long version, boolean includeCurrentVersion) {
        String rangeOperator = includeCurrentVersion ? "lte" : "lt";
        StringQuery query = new StringQuery(json(Map.of(
                "bool", Map.of(
                        "filter", List.of(
                                Map.of("term", Map.of("source_type", sourceType)),
                                Map.of("term", Map.of("source_id", sourceId)),
                                Map.of("term", Map.of("status", "ACTIVE")),
                                Map.of("range", Map.of(
                                        "content_version", Map.of(rangeOperator, version))))))));
        ByQueryResponse response = operations.delete(
                query,
                KnowledgeChunkDocument.class,
                writeIndex());
        return response.getDeleted();
    }

    private void bulkIndexWithExternalVersion(
            List<KnowledgeChunkDocument> documents, long version) {
        List<IndexQuery> queries = documents.stream()
                .map(document -> {
                    IndexQuery query = new IndexQuery();
                    query.setId(document.getChunkId());
                    query.setObject(document);
                    query.setVersion(version);
                    return query;
                })
                .toList();
        operations.bulkIndex(queries, writeIndex());
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

    private Map<String, Object> sourceState(
            String sourceType, String sourceId, long version, String status) {
        return Map.of(
                "chunk_id", sourceStateId(sourceType, sourceId),
                "source_type", sourceType,
                "source_id", sourceId,
                "title", "source version state",
                "content", "source version state",
                "source_url", "mylesson://source-state/"
                        + sourceType.toLowerCase(Locale.ROOT) + "/" + sourceId,
                "content_version", version,
                "chunk_index", -1,
                "content_hash", "source-state-" + version,
                "status", status);
    }

    private String sourceStateId(String sourceType, String sourceId) {
        return "__source__:" + sourceType + ":" + sourceId;
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

}
