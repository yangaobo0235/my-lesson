package com.yangaobo.ai.evaluation.controller;

import com.yangaobo.ai.evaluation.model.EvaluationResultView;
import com.yangaobo.ai.evaluation.model.EvaluationSummary;
import com.yangaobo.ai.evaluation.repository.EvaluationQueryRepository;
import com.yangaobo.ai.evaluation.model.EvaluationReport;
import com.yangaobo.ai.evaluation.service.EvaluationRunnerService;
import com.yangaobo.ai.knowledge.service.KnowledgeAdminGuard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping({
        "/api/v1/ai/admin/evaluation",
        "/api/v1/ai/admin/evaluations"})
public class EvaluationAdminController {

    private final EvaluationQueryRepository repository;
    private final KnowledgeAdminGuard adminGuard;
    private final EvaluationRunnerService runnerService;

    public EvaluationAdminController(
            EvaluationQueryRepository repository,
            KnowledgeAdminGuard adminGuard,
            EvaluationRunnerService runnerService) {
        this.repository = repository;
        this.adminGuard = adminGuard;
        this.runnerService = runnerService;
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

    @PostMapping("/run")
    public EvaluationReport run(
            @RequestParam(defaultValue = "deterministic") String mode) {
        adminGuard.requireAdmin();
        return runnerService.run(mode);
    }

    @GetMapping("/{id}")
    public EvaluationReport report(@PathVariable UUID id) {
        adminGuard.requireAdmin();
        return runnerService.find(id);
    }
}
