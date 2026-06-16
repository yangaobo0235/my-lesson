package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.config.BusinessToolProperties;
import com.yangaobo.ai.tool.dto.CourseIdRequest;
import com.yangaobo.ai.tool.dto.SearchCoursesRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CourseTools {

    private final AiBusinessGateway businessGateway;
    private final BusinessToolProperties properties;

    public CourseTools(
            AiBusinessGateway businessGateway,
            BusinessToolProperties properties) {
        this.businessGateway = businessGateway;
        this.properties = properties;
    }

    public List<CourseAiClient.CourseSummary> searchCourses(
            SearchCoursesRequest request) {
        int requestedLimit = request.limit() == null
                ? 5
                : request.limit();
        int safeLimit = Math.max(
                1,
                Math.min(
                        requestedLimit,
                        properties.getMaxCourseSearchLimit()));
        return businessGateway.searchCourses(
                request.keyword().trim(),
                safeLimit);
    }

    public CourseAiClient.CourseKnowledge getCourseDetail(
            CourseIdRequest request) {
        return businessGateway.getCourse(request.courseId());
    }
}
