package com.yangaobo.ai.knowledge.controller;

import com.yangaobo.ai.knowledge.model.KnowledgeIndexStatus;
import com.yangaobo.ai.approval.exception.ApprovalStateException;
import com.yangaobo.ai.knowledge.model.KnowledgeSourceView;
import com.yangaobo.ai.knowledge.repository.KnowledgeSourceRepository;
import com.yangaobo.ai.knowledge.service.KnowledgeAdminGuard;
import com.yangaobo.ai.knowledge.service.KnowledgeIndexService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/ai/admin/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeIndexService knowledgeIndexService;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeAdminGuard adminGuard;

    public KnowledgeAdminController(
            KnowledgeIndexService knowledgeIndexService,
            KnowledgeSourceRepository sourceRepository,
            KnowledgeAdminGuard adminGuard) {
        this.knowledgeIndexService = knowledgeIndexService;
        this.sourceRepository = sourceRepository;
        this.adminGuard = adminGuard;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<KnowledgeIndexStatus> rebuild() {
        adminGuard.requireAdmin();
        throw new ApprovalStateException(
                "APPROVAL_REQUIRED",
                "知识索引重建必须通过 AI 对话发起，并在审批列表中确认");
    }

    @GetMapping("/status")
    public KnowledgeIndexStatus status() {
        adminGuard.requireAdmin();
        return knowledgeIndexService.status();
    }

    @GetMapping("/sources")
    public List<KnowledgeSourceView> sources(
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @Min(1) @Max(200) Integer limit) {
        adminGuard.requireAdmin();
        return sourceRepository.recentSources(
                sourceType,
                status,
                limit == null ? 100 : limit);
    }

    @PostMapping("/sources/{sourceType}/{sourceId}/retry")
    public KnowledgeSourceView retrySource(
            @PathVariable String sourceType,
            @PathVariable String sourceId) {
        adminGuard.requireAdmin();
        knowledgeIndexService.retrySource(sourceType, sourceId);
        return sourceRepository
                .findView(sourceType, sourceId)
                .orElseThrow(() -> new IllegalStateException(
                        "Knowledge source retry did not produce a source row"));
    }
}
