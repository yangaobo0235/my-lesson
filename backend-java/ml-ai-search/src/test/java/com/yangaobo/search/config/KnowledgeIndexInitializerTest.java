package com.yangaobo.search.config;

import com.yangaobo.search.es.KnowledgeChunkDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexInitializerTest {

    @Mock
    private ElasticsearchOperations operations;

    @Mock
    private IndexOperations indexOperations;

    @Test
    void addsAliasWhenPhysicalIndexHasNoAliases() {
        AiSearchProperties properties = new AiSearchProperties();
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getAliasesForIndex(properties.getIndexName())).thenReturn(Map.of());

        new KnowledgeIndexInitializer(operations, properties).run(null);

        ArgumentCaptor<AliasActions> actions = ArgumentCaptor.forClass(AliasActions.class);
        verify(indexOperations).alias(actions.capture());
        assertFalse(actions.getValue().getActions().isEmpty());
    }
}
