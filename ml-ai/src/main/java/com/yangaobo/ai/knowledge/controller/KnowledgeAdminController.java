package com.yangaobo.ai.knowledge.controller;

import com.yangaobo.ai.knowledge.model.KnowledgeIndexStatus;
import com.yangaobo.ai.approval.exception.ApprovalStateException;
import com.yangaobo.ai.knowledge.service.KnowledgeAdminGuard;
import com.yangaobo.ai.knowledge.service.KnowledgeIndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/admin/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeIndexService knowledgeIndexService;
    private final KnowledgeAdminGuard adminGuard;

    public KnowledgeAdminController(
            KnowledgeIndexService knowledgeIndexService,
            KnowledgeAdminGuard adminGuard) {
        this.knowledgeIndexService = knowledgeIndexService;
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
}
