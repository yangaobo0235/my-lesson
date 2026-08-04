package com.yangaobo.controller.internal;

import com.yangaobo.dto.ai.UserProfileAiDTO;
import com.yangaobo.dto.ai.UserRoleAiDTO;
import com.yangaobo.security.SecurityContext;
import com.yangaobo.service.ai.UserAiQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/agent/me")
public class AgentUserToolController {

    private final UserAiQueryService queryService;

    public AgentUserToolController(UserAiQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/profile")
    public UserProfileAiDTO profile() {
        return queryService.profile(SecurityContext.requireUserId());
    }

    @GetMapping("/roles")
    public List<UserRoleAiDTO> roles() {
        return queryService.roles(SecurityContext.requireUserId());
    }
}
