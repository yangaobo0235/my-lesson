package com.yangaobo.controller.internal;

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
@RequestMapping("/internal/v1/agent")
public class AgentCourseToolController {

    private final CourseAiQueryService queryService;

    public AgentCourseToolController(CourseAiQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/courses/search")
    public List<CourseSummaryAiDTO> search(
            @RequestParam(defaultValue = "")
            @Size(max = 200) String keyword,
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(50) int limit) {
        return queryService.search(keyword, limit);
    }

    @GetMapping("/courses/{courseId}")
    public CourseKnowledgeDTO course(@PathVariable Long courseId) {
        return queryService.getCourse(courseId);
    }

    @GetMapping("/course-categories")
    public List<CategoryAiDTO> categories() {
        return queryService.categories();
    }
}
