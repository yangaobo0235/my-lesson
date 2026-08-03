package com.yangaobo.ai.tool.controller;

import com.yangaobo.ai.knowledge.service.KnowledgeAdminGuard;
import com.yangaobo.ai.tool.model.ToolCallView;
import com.yangaobo.ai.tool.repository.ToolCallRepository;
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
@RequestMapping("/api/v1/ai/admin/tools")
public class ToolAdminController {

    private final ToolCallRepository repository;
    private final KnowledgeAdminGuard adminGuard;

    public ToolAdminController(
            ToolCallRepository repository,
            KnowledgeAdminGuard adminGuard) {
        this.repository = repository;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/calls")
    public List<ToolCallView> calls(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String accessType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @Min(1) @Max(200) Integer limit) {
        adminGuard.requireAdmin();
        return repository.recentCalls(
                userId,
                toolName,
                accessType,
                status,
                limit == null ? 100 : limit);
    }
}
