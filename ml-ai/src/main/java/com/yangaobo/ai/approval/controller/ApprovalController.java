package com.yangaobo.ai.approval.controller;

import com.yangaobo.ai.approval.model.ApprovalTask;
import com.yangaobo.ai.approval.service.ApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public List<ApprovalTask> list() {
        return approvalService.list();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalTask> approve(
            @PathVariable UUID id) {
        return ResponseEntity.ok(approvalService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApprovalTask> reject(
            @PathVariable UUID id) {
        return ResponseEntity.ok(approvalService.reject(id));
    }
}
