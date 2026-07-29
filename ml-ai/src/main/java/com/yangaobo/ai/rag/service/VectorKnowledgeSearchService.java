package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.RetrievalCandidate;
import com.yangaobo.ai.rag.model.SearchHit;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VectorKnowledgeSearchService {

    private final VectorStore vectorStore;
    private final RagProperties properties;

    public VectorKnowledgeSearchService(
            VectorStore vectorStore,
            RagProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<RetrievalCandidate> search(String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(properties.getVectorTopK())
                .similarityThreshold(
                        properties.getVectorSimilarityThreshold())
                .build();
        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null) {
            return List.of();
        }
        return documents.stream()
                .map(this::toCandidate)
                .toList();
    }

    private RetrievalCandidate toCandidate(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String sourceType = value(metadata, "source_type");
        String sourceId = value(metadata, "source_id");
        int chunkIndex = integer(metadata.get("chunk_index"));
        double score = document.getScore() == null
                ? 0.0
                : document.getScore();
        SearchHit hit = new SearchHit(
                sourceType,
                sourceId,
                value(metadata, "title"),
                document.getText(),
                value(metadata, "source_url"),
                score,
                longValue(metadata.get("version")),
                valueOrDefault(metadata, "visibility_status", "ACTIVE"));
        return new RetrievalCandidate(
                hit,
                fusionKey(sourceType, sourceId, chunkIndex),
                score);
    }

    private String value(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value == null ? "0" : value.toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value == null ? "1" : value.toString());
        } catch (NumberFormatException exception) {
            return 1L;
        }
    }

    private String valueOrDefault(
            Map<String, Object> metadata,
            String key,
            String defaultValue) {
        String result = value(metadata, key);
        return result.isBlank() ? defaultValue : result;
    }

    private String fusionKey(
            String sourceType,
            String sourceId,
            int chunkIndex) {
        return sourceType + ":" + sourceId + ":" + chunkIndex;
    }
}
