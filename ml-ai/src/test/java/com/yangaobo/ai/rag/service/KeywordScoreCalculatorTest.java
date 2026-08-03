package com.yangaobo.ai.rag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordScoreCalculatorTest {

    private final KeywordScoreCalculator calculator =
            new KeywordScoreCalculator();

    @Test
    void extractsLatinAndChineseTermsFromMixedQuery() {
        assertThat(calculator.terms("Java课程"))
                .contains("java", "课程");
    }
}
