package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.model.RetrievalCandidate;
import com.yangaobo.ai.rag.model.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionServiceTest {

    private final ReciprocalRankFusionService service =
            new ReciprocalRankFusionService();

    @Test
    void shouldMergeSameChunkAndPromoteHitsFoundByBothSearches() {
        RetrievalCandidate vectorCourse = candidate(
                "COURSE:1:0",
                "COURSE",
                "1",
                "Java课程",
                0.72);
        RetrievalCandidate vectorEpisodes = candidate(
                "COURSE_EPISODES:2:0",
                "COURSE_EPISODES",
                "2",
                "Java文章",
                0.68);
        RetrievalCandidate keywordCourse = candidate(
                "COURSE:1:0",
                "COURSE",
                "1",
                "Java课程",
                0.90);

        ReciprocalRankFusionService.FusionResult result = service.fuse(
                List.of(vectorCourse, vectorEpisodes),
                List.of(keywordCourse),
                20);

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().get(0).fusionKey())
                .isEqualTo("COURSE:1:0");
        assertThat(result.candidates().get(0).evidenceScore())
                .isEqualTo(0.90);
        assertThat(result.highestEvidenceScore()).isEqualTo(0.90);
    }

    private RetrievalCandidate candidate(
            String key,
            String sourceType,
            String sourceId,
            String title,
            double score) {
        return new RetrievalCandidate(
                new SearchHit(
                        sourceType,
                        sourceId,
                        title,
                        title + "正文",
                        "http://localhost/" + sourceId,
                        score),
                key,
                score);
    }
}
