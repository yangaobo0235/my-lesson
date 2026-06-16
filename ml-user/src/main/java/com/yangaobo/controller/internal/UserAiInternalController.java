package com.yangaobo.controller.internal;

import com.yangaobo.dto.ai.UserProfileAiDTO;
import com.yangaobo.dto.ai.UserRoleAiDTO;
import com.yangaobo.service.ai.UserAiQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/ai/users/{userId}")
public class UserAiInternalController {

    private final UserAiQueryService userAiQueryService;

    public UserAiInternalController(UserAiQueryService userAiQueryService) {
        this.userAiQueryService = userAiQueryService;
    }

    @GetMapping("/profile")
    public UserProfileAiDTO profile(@PathVariable("userId") Long userId) {
        return userAiQueryService.profile(userId);
    }

    @GetMapping("/roles")
    public List<UserRoleAiDTO> roles(@PathVariable("userId") Long userId) {
        return userAiQueryService.roles(userId);
    }
}
