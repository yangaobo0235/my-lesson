package com.yangaobo.ai.recommendation.controller;

import com.yangaobo.ai.recommendation.model.CourseRecommendationRequest;
import com.yangaobo.ai.recommendation.model.CourseRecommendationResponse;
import com.yangaobo.ai.recommendation.service.CourseRecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/course-recommendations")
public class CourseRecommendationController {

    private final CourseRecommendationService service;

    public CourseRecommendationController(CourseRecommendationService service) {
        this.service = service;
    }

    @PostMapping
    public CourseRecommendationResponse recommend(
            @Valid @RequestBody CourseRecommendationRequest request) {
        return service.recommend(request);
    }
}
