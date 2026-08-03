package com.yangaobo.ai.workflow.controller;

import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.workflow.model.LearningPlanAdjustmentRequest;
import com.yangaobo.ai.workflow.model.LearningPlanDraftRecord;
import com.yangaobo.ai.workflow.repository.LearningPlanDraftRepository;
import com.yangaobo.ai.workflow.service.LearningPlanWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import com.yangaobo.ai.tool.model.LearningPlan;

@RestController
@RequestMapping("/api/v1/ai/learning-plan-drafts")
public class LearningPlanDraftController {

    private final LearningPlanWorkflowService workflowService;
    private final LearningPlanDraftRepository repository;

    public LearningPlanDraftController(
            LearningPlanWorkflowService workflowService,
            LearningPlanDraftRepository repository) {
        this.workflowService = workflowService;
        this.repository = repository;
    }

    @PostMapping("/{id}/adjustments")
    public LearningPlanDraftRecord adjust(
            @PathVariable UUID id,
            @Valid @RequestBody LearningPlanAdjustmentRequest request) {
        return workflowService.adjust(id, request);
    }

    @GetMapping
    public List<LearningPlanDraftRecord> list() {
        return repository.findAllOwned(
                UserContext.requireUser().id(), 50);
    }

    @GetMapping("/{id}/versions")
    public List<LearningPlanDraftRecord> versions(@PathVariable UUID id) {
        return repository.findVersionsOwned(
                id, UserContext.requireUser().id());
    }

    @PostMapping("/{id}/confirm")
    public LearningPlan confirm(@PathVariable UUID id) {
        return workflowService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        workflowService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
