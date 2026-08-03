package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.exception.DownstreamServiceException;
import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.RetrievalCandidate;
import com.yangaobo.ai.rag.model.SearchHit;
import com.yangaobo.ai.service.AiBusinessGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class KeywordKnowledgeSearchService {

    private static final Logger log =
            LoggerFactory.getLogger(KeywordKnowledgeSearchService.class);

    private final AiBusinessGateway businessGateway;
    private final KeywordScoreCalculator scoreCalculator;
    private final RagProperties properties;

    public KeywordKnowledgeSearchService(
            AiBusinessGateway businessGateway,
            KeywordScoreCalculator scoreCalculator,
            RagProperties properties) {
        this.businessGateway = businessGateway;
        this.scoreCalculator = scoreCalculator;
        this.properties = properties;
    }

    public List<RetrievalCandidate> search(String query) {
        List<SearchHit> hits = new ArrayList<>();
        searchCourses(query, hits);

        List<SearchHit> ranked = hits.stream()
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(properties.getKeywordTopK())
                .toList();
        List<RetrievalCandidate> result = new ArrayList<>(ranked.size());
        for (SearchHit hit : ranked) {
            result.add(new RetrievalCandidate(
                    hit,
                    hit.sourceType() + ":" + hit.sourceId() + ":0",
                    hit.score()));
        }
        return List.copyOf(result);
    }

    private void searchCourses(String query, List<SearchHit> hits) {
        try {
            List<CourseAiClient.CourseSummary> courses =
                    businessGateway.searchCourses(
                            query,
                            properties.getKeywordTopK());
            for (int index = 0; index < courses.size(); index++) {
                CourseAiClient.CourseSummary course = courses.get(index);
                String snippet = join(
                        "作者：" + safe(course.author()),
                        "分类：" + safe(course.category()));
                double score = scoreCalculator.score(
                        query,
                        course.title(),
                        snippet,
                        index + 1);
                hits.add(new SearchHit(
                        "COURSE",
                        course.id().toString(),
                        course.title(),
                        snippet,
                        sourceUrl(
                                "/course-server/api/v1/course/select/"
                                        + course.id()),
                        score));
            }
        } catch (DownstreamServiceException exception) {
            log.warn("Course keyword search unavailable");
        }
    }

    private String sourceUrl(String path) {
        String base = properties.getSourceBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private String join(String... values) {
        return String.join("\n", values);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
