package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.knowledge.model.KnowledgeChunk;
import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicKnowledgeChunkerTest {

    private final DeterministicKnowledgeChunker chunker =
            new DeterministicKnowledgeChunker();

    @Test
    void shouldChunkLongChineseContentWithoutLosingText() {
        String sourceContent = "课程正文".repeat(900);
        KnowledgeDocument source = source(sourceContent);

        List<KnowledgeChunk> chunks = chunker.chunk(source);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks)
                .allSatisfy(chunk ->
                        assertThat(chunk.content().length())
                                .isLessThanOrEqualTo(1000));

        StringBuilder rebuilt = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            String body = body(chunks.get(index).content());
            if (index == 0) {
                rebuilt.append(body);
                continue;
            }
            String previousBody = body(chunks.get(index - 1).content());
            String overlap = previousBody.substring(previousBody.length() - 100);
            assertThat(body).startsWith(overlap + "\n");
            rebuilt.append(body.substring(overlap.length() + 1));
        }
        assertThat(rebuilt.toString()).isEqualTo(sourceContent);
    }

    @Test
    void shouldBeDeterministicAndIncludePrefixMetadata() {
        KnowledgeDocument source = source(
                "<p>第一段课程内容。</p><p>第二段课程内容。</p>");

        List<KnowledgeChunk> first = chunker.chunk(source);
        List<KnowledgeChunk> second = chunker.chunk(source);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(1);
        assertThat(first.get(0).content())
                .startsWith("标题：测试课程\n作者：测试作者\n分类：Java")
                .doesNotContain("<p>");
    }

    private KnowledgeDocument source(String content) {
        return new KnowledgeDocument(
                "COURSE",
                "1",
                "测试课程",
                content,
                "http://localhost/course/1",
                1L,
                "hash",
                Map.of("author", "测试作者", "category", "Java"));
    }

    private String body(String chunkContent) {
        return chunkContent.substring(chunkContent.indexOf("\n\n") + 2);
    }
}
