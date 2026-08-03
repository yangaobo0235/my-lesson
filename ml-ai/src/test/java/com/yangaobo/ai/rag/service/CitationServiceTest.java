package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.Citation;
import com.yangaobo.ai.rag.model.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationServiceTest {

    private final CitationService service =
            new CitationService(new RagProperties());

    @Test
    void shouldRemoveInvalidReferencesAndReturnOnlyUsedCitations() {
        List<Citation> citations = service.create(List.of(
                hit("1", "Java课程"),
                hit("2", "Spring课程")));

        CitationService.ValidatedAnswer result = service.validate(
                "Java课程适合入门[1]，不存在的内容[9]。",
                citations);

        assertThat(result.answer())
                .isEqualTo("Java课程适合入门[1]，不存在的内容。");
        assertThat(result.citations())
                .extracting(Citation::index)
                .containsExactly(1);
    }

    private SearchHit hit(String id, String title) {
        return new SearchHit(
                "COURSE",
                id,
                title,
                title + "的详细介绍",
                "http://localhost/course/" + id,
                0.8);
    }
}
