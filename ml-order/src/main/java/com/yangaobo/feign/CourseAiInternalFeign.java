package com.yangaobo.feign;

import com.yangaobo.dto.ai.CourseKnowledgeAiDTO;
import com.yangaobo.dto.ai.CourseSummaryAiDTO;
import com.yangaobo.dto.ai.InternalAiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "ml-course", contextId = "orderCourseAiInternalFeign")
public interface CourseAiInternalFeign {

    @GetMapping("/internal/ai/courses/{courseId}")
    InternalAiResponse<CourseKnowledgeAiDTO> getCourse(
            @PathVariable("courseId") Long courseId);

    @GetMapping("/internal/ai/courses/search")
    InternalAiResponse<List<CourseSummaryAiDTO>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam("limit") int limit);
}
