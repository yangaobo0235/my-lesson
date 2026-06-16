package com.yangaobo.ai.tool.controller;

import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/ai/plans")
public class LearningPlanController {

    private static final int DEFAULT_LIMIT = 50;

    private final LearningPlanRepository repository;

    public LearningPlanController(LearningPlanRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<LearningPlan> list(
            @RequestParam(required = false)
            @Min(1) @Max(100) Integer limit) {
        int safeLimit = limit == null ? DEFAULT_LIMIT : limit;
        return repository.findAllByUser(
                UserContext.requireUser().id(),
                safeLimit);
    }
}
