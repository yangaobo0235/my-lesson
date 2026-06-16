package com.yangaobo.controller.internal;

import com.yangaobo.dto.ai.AiCursorPage;
import com.yangaobo.dto.ai.CategoryAiDTO;
import com.yangaobo.dto.ai.CourseKnowledgeDTO;
import com.yangaobo.dto.ai.CourseSummaryAiDTO;
import com.yangaobo.service.ai.CourseAiQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/ai")
public class CourseAiInternalController {

    private final CourseAiQueryService courseAiQueryService;

    public CourseAiInternalController(CourseAiQueryService courseAiQueryService) {
        this.courseAiQueryService = courseAiQueryService;
    }

    @GetMapping("/courses/{id}")
    public CourseKnowledgeDTO getCourse(@PathVariable("id") Long id) {
        return courseAiQueryService.getCourse(id);
    }

    @GetMapping("/courses/search")
    public List<CourseSummaryAiDTO> search(
            @RequestParam(name = "keyword", defaultValue = "")
            @Size(max = 42) String keyword,
            @RequestParam(name = "limit", defaultValue = "10")
            @Min(1) @Max(50) int limit) {
        return courseAiQueryService.search(keyword, limit);
    }

    @GetMapping("/courses/knowledge")
    public AiCursorPage<CourseKnowledgeDTO> knowledge(
            @RequestParam(name = "cursor", defaultValue = "0")
            @Min(0) Long cursor,
            @RequestParam(name = "size", defaultValue = "100")
            @Min(1) @Max(200) int size) {
        return courseAiQueryService.knowledge(cursor, size);
    }

    @GetMapping("/categories")
    public List<CategoryAiDTO> categories() {
        return courseAiQueryService.categories();
    }
}
