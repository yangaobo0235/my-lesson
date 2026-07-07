package com.yangaobo.ai.tool.controller;

import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.tool.dto.LearningPlanProgressRequest;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import com.yangaobo.ai.tool.service.LearningPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/ai/plans")
public class LearningPlanController {

    private static final int DEFAULT_LIMIT = 50;

    private final LearningPlanRepository repository;
    private final LearningPlanService service;

    public LearningPlanController(
            LearningPlanRepository repository,
            LearningPlanService service) {
        this.repository = repository;
        this.service = service;
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

    @PatchMapping("/{planId}/progress")
    public LearningPlan updateProgress(
            @PathVariable UUID planId,
            @Valid @RequestBody LearningPlanProgressRequest request) {
        return service.updateProgress(planId, request);
    }
}
