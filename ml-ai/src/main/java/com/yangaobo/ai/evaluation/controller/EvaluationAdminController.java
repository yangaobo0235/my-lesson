package com.yangaobo.ai.evaluation.controller;

import com.yangaobo.ai.evaluation.model.EvaluationResultView;
import com.yangaobo.ai.evaluation.model.EvaluationSummary;
import com.yangaobo.ai.evaluation.repository.EvaluationQueryRepository;
import com.yangaobo.ai.knowledge.service.KnowledgeAdminGuard;
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
@RequestMapping("/api/v1/ai/admin/evaluation")
public class EvaluationAdminController {

    private final EvaluationQueryRepository repository;
    private final KnowledgeAdminGuard adminGuard;

    public EvaluationAdminController(
            EvaluationQueryRepository repository,
            KnowledgeAdminGuard adminGuard) {
        this.repository = repository;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/summary")
    public EvaluationSummary summary() {
        adminGuard.requireAdmin();
        return repository.summary();
    }

    @GetMapping("/results")
    public List<EvaluationResultView> results(
            @RequestParam(required = false)
            @Min(1) @Max(200) Integer limit) {
        adminGuard.requireAdmin();
        return repository.recentResults(limit == null ? 100 : limit);
    }
}
