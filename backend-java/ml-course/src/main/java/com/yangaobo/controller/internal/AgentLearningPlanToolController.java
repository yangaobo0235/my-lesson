package com.yangaobo.controller.internal;

import com.yangaobo.dto.ai.AgentLearningPlanModels.AdjustmentRequest;
import com.yangaobo.dto.ai.AgentLearningPlanModels.CreateDraftRequest;
import com.yangaobo.dto.ai.AgentLearningPlanModels.DraftView;
import com.yangaobo.dto.ai.AgentLearningPlanModels.PlanView;
import com.yangaobo.dto.ai.AgentLearningPlanModels.ProgressRequest;
import com.yangaobo.security.SecurityContext;
import com.yangaobo.service.ai.AgentLearningPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/v1/agent/me")
public class AgentLearningPlanToolController {

    private final AgentLearningPlanService service;

    public AgentLearningPlanToolController(AgentLearningPlanService service) {
        this.service = service;
    }

    @PostMapping("/learning-plan-drafts")
    public DraftView createDraft(
            @Valid @RequestBody CreateDraftRequest request) {
        return service.createDraft(SecurityContext.requireUserId(), request);
    }

    @GetMapping("/learning-plan-drafts")
    public List<DraftView> drafts() {
        return service.drafts(SecurityContext.requireUserId());
    }

    @GetMapping("/learning-plan-drafts/{draftId}/versions")
    public List<DraftView> versions(@PathVariable UUID draftId) {
        return service.versions(draftId, SecurityContext.requireUserId());
    }

    @PostMapping("/learning-plan-drafts/{draftId}/adjustments")
    public DraftView adjust(
            @PathVariable UUID draftId,
            @Valid @RequestBody AdjustmentRequest request) {
        return service.adjust(
                draftId,
                SecurityContext.requireUserId(),
                request);
    }

    @PostMapping("/learning-plan-drafts/{draftId}/confirm")
    public PlanView confirm(@PathVariable UUID draftId) {
        return service.confirm(draftId, SecurityContext.requireUserId());
    }

    @PostMapping("/learning-plan-drafts/{draftId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID draftId) {
        service.cancel(draftId, SecurityContext.requireUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/learning-plans")
    public List<PlanView> plans(
            @RequestParam(defaultValue = "50")
            @Min(1) @Max(100) int limit) {
        return service.plans(SecurityContext.requireUserId(), limit);
    }

    @PatchMapping("/learning-plans/{planId}/progress")
    public PlanView updateProgress(
            @PathVariable UUID planId,
            @Valid @RequestBody ProgressRequest request) {
        return service.updateProgress(
                planId,
                SecurityContext.requireUserId(),
                request);
    }
}
