package com.yangaobo.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@FeignClient(name = "ml-course", contextId = "courseAiClient")
public interface CourseAiClient {

    @GetMapping("/internal/ai/courses/{id}")
    InternalAiResponse<CourseKnowledge> getCourse(@PathVariable Long id);

    @GetMapping("/internal/ai/courses/search")
    InternalAiResponse<List<CourseSummary>> search(
            @RequestParam String keyword,
            @RequestParam int limit);

    @GetMapping("/internal/ai/courses/knowledge")
    InternalAiResponse<CursorPage<CourseKnowledge>> knowledge(
            @RequestParam Long cursor,
            @RequestParam int size);

    @GetMapping("/internal/ai/categories")
    InternalAiResponse<List<Category>> categories();

    record CourseSummary(
            Long id,
            String title,
            String author,
            String category,
            Double price,
            String cover,
            LocalDateTime updated
    ) {
    }

    record CourseKnowledge(
            Long id,
            String title,
            String author,
            String category,
            String description,
            String detail,
            List<String> episodeTitles,
            LocalDateTime updated
    ) {
    }

    record Category(Long id, String title) {
    }

    record CursorPage<T>(List<T> items, Long nextCursor, boolean hasMore) {
    }
}
