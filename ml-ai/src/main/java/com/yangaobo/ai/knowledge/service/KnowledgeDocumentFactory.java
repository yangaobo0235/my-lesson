package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.knowledge.model.KnowledgeDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeDocumentFactory {

    public static final String COURSE = "COURSE";
    public static final String COURSE_EPISODES = "COURSE_EPISODES";

    private final KnowledgeContentHasher hasher;
    private final String sourceBaseUrl;

    public KnowledgeDocumentFactory(
            KnowledgeContentHasher hasher,
            @Value("${ai.knowledge.source-base-url:http://127.0.0.1:24101}")
            String sourceBaseUrl) {
        this.hasher = hasher;
        this.sourceBaseUrl = trimTrailingSlash(sourceBaseUrl);
    }

    public List<KnowledgeDocument> fromCourse(CourseAiClient.CourseKnowledge course) {
        List<KnowledgeDocument> result = new ArrayList<>(2);
        String sourceId = course.id().toString();
        String sourceUrl = sourceBaseUrl
                + "/course-server/api/v1/course/select/"
                + course.id();

        Map<String, Object> courseMetadata = metadata(
                "author", course.author(),
                "category", course.category());
        String courseContent = joinNonBlank(
                course.description(),
                course.detail());
        result.add(create(
                COURSE,
                sourceId,
                course.title(),
                courseContent,
                sourceUrl,
                version(course.updated()),
                courseMetadata));

        if (course.episodeTitles() != null && !course.episodeTitles().isEmpty()) {
            Map<String, Object> episodeMetadata = metadata(
                    "author", course.author(),
                    "category", course.category(),
                    "course_id", course.id());
            StringBuilder episodes = new StringBuilder("课程分集列表");
            for (int index = 0; index < course.episodeTitles().size(); index++) {
                episodes.append('\n')
                        .append(index + 1)
                        .append(". ")
                        .append(course.episodeTitles().get(index));
            }
            result.add(create(
                    COURSE_EPISODES,
                    sourceId,
                    course.title() + " - 分集",
                    episodes.toString(),
                    sourceUrl,
                    version(course.updated()),
                    episodeMetadata));
        }
        return result;
    }

    private KnowledgeDocument create(
            String sourceType,
            String sourceId,
            String title,
            String content,
            String sourceUrl,
            long version,
            Map<String, Object> metadata) {
        Map<String, Object> immutableMetadata = Map.copyOf(metadata);
        String normalizedContent = content == null || content.isBlank() ? title : content;
        String contentHash = hasher.hash(
                sourceType,
                sourceId,
                title,
                normalizedContent,
                sourceUrl,
                immutableMetadata);
        return new KnowledgeDocument(
                sourceType,
                sourceId,
                title,
                normalizedContent,
                sourceUrl,
                version,
                contentHash,
                immutableMetadata);
    }

    private Map<String, Object> metadata(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            Object value = pairs[index + 1];
            if (value != null && !value.toString().isBlank()) {
                result.put(pairs[index].toString(), value);
            }
        }
        return result;
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join("\n\n", parts);
    }

    private long version(LocalDateTime updated) {
        return updated == null
                ? 1L
                : updated.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
