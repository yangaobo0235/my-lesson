package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.knowledge.model.KnowledgeChunk;
import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import com.yangaobo.ai.knowledge.model.KnowledgeSourceKey;
import com.yangaobo.ai.knowledge.repository.KnowledgeSourceRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeSourceIndexer {

    private final VectorStore vectorStore;
    private final KnowledgeSourceRepository sourceRepository;
    private final DeterministicKnowledgeChunker chunker;
    private final String embeddingModel;
    private final String embeddingVersion;

    public KnowledgeSourceIndexer(
            VectorStore vectorStore,
            KnowledgeSourceRepository sourceRepository,
            DeterministicKnowledgeChunker chunker,
            @Value("${spring.ai.dashscope.embedding.options.model:text-embedding-v4}")
            String embeddingModel,
            @Value("${ai.knowledge.embedding-version:v1}")
            String embeddingVersion) {
        this.vectorStore = vectorStore;
        this.sourceRepository = sourceRepository;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.embeddingVersion = embeddingVersion;
    }

    @Transactional
    public IndexOutcome index(KnowledgeDocument source) {
        List<KnowledgeChunk> chunks = chunker.chunk(source);
        var current = sourceRepository.find(
                source.sourceType(),
                source.sourceId());
        if (current.filter(state -> state.version() > source.version()).isPresent()) {
            return new IndexOutcome(false, 0);
        }
        boolean unchanged = current
                .filter(state -> "ACTIVE".equals(state.status()))
                .filter(state -> source.contentHash().equals(state.contentHash()))
                .isPresent();
        if (unchanged
                && sourceRepository.countChunks(
                        source.sourceType(),
                        source.sourceId(),
                        embeddingModel,
                        embeddingVersion) == chunks.size()) {
            return new IndexOutcome(false, chunks.size());
        }

        deleteVectors(source.sourceType(), source.sourceId());
        vectorStore.add(toDocuments(source, chunks));
        sourceRepository.upsert(source);
        return new IndexOutcome(true, chunks.size());
    }

    @Transactional
    public void delete(KnowledgeSourceKey source) {
        long version = sourceRepository.find(source.sourceType(), source.sourceId())
                .map(KnowledgeSourceRepository.SourceState::version)
                .orElse(1L);
        delete(source, version);
    }

    @Transactional
    public boolean delete(KnowledgeSourceKey source, long version) {
        boolean newerStateExists = sourceRepository
                .find(source.sourceType(), source.sourceId())
                .filter(state -> state.version() > version)
                .isPresent();
        if (newerStateExists) {
            return false;
        }
        deleteVectors(source.sourceType(), source.sourceId());
        sourceRepository.markDeleted(source, version);
        return true;
    }

    private void deleteVectors(String sourceType, String sourceId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        vectorStore.delete(builder.and(
                builder.eq("source_type", sourceType),
                builder.eq("source_id", sourceId)).build());
    }

    private List<Document> toDocuments(
            KnowledgeDocument source,
            List<KnowledgeChunk> chunks) {
        List<Document> documents = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> metadata = new LinkedHashMap<>(source.metadata());
            metadata.put("source_type", source.sourceType());
            metadata.put("source_id", source.sourceId());
            metadata.put("title", source.title());
            metadata.put("source_url", source.sourceUrl());
            metadata.put("version", source.version());
            metadata.put("visibility_status", "ACTIVE");
            metadata.put("chunk_index", chunk.index());
            metadata.put("content_hash", source.contentHash());
            metadata.put("embedding_model", embeddingModel);
            metadata.put("embedding_version", embeddingVersion);

            String idSeed = source.sourceType()
                    + ":"
                    + source.sourceId()
                    + ":"
                    + chunk.index()
                    + ":"
                    + source.contentHash();
            String documentId = UUID.nameUUIDFromBytes(
                    idSeed.getBytes(StandardCharsets.UTF_8)).toString();
            documents.add(new Document(documentId, chunk.content(), metadata));
        }
        return documents;
    }

    public record IndexOutcome(
            boolean indexed,
            int chunkCount
    ) {
    }
}
