package com.yangaobo.search.config;

import com.yangaobo.search.es.KnowledgeChunkDocument;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations operations;
    private final AiSearchProperties properties;

    public KnowledgeIndexInitializer(
            ElasticsearchOperations operations,
            AiSearchProperties properties) {
        this.operations = operations;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoCreateIndex()) {
            return;
        }
        IndexCoordinates physicalIndex = IndexCoordinates.of(properties.getIndexName());
        IndexOperations indexOperations = operations.indexOps(physicalIndex);
        if (!indexOperations.exists()) {
            IndexOperations documentOperations = operations.indexOps(KnowledgeChunkDocument.class);
            indexOperations.create(
                    documentOperations.createSettings(KnowledgeChunkDocument.class),
                    documentOperations.createMapping(KnowledgeChunkDocument.class));
        }
        boolean aliasExists = indexOperations
                .getAliasesForIndex(properties.getIndexName())
                .values()
                .stream()
                .flatMap(java.util.Collection::stream)
                .anyMatch(alias -> properties.getIndexAlias().equals(alias.getAlias()));
        if (!aliasExists) {
            AliasActionParameters parameters = AliasActionParameters.builder()
                    .withIndices(properties.getIndexName())
                    .withAliases(properties.getIndexAlias())
                    .withIsWriteIndex(true)
                    .build();
            indexOperations.alias(new AliasActions(new AliasAction.Add(parameters)));
        }
    }
}
