package com.yangaobo.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "ml-user", contextId = "userAiClient")
public interface UserAiClient {

    @GetMapping("/internal/ai/users/{userId}/profile")
    InternalAiResponse<UserProfile> profile(@PathVariable Long userId);

    @GetMapping("/internal/ai/users/{userId}/roles")
    InternalAiResponse<List<UserRole>> roles(@PathVariable Long userId);

    record UserProfile(
            Long id,
            String username,
            String nickname,
            String email,
            String province,
            String avatar,
            String zodiac,
            String maskedPhone,
            Integer gender,
            Integer age,
            String info
    ) {
    }

    record UserRole(Long id, String title) {
    }
}
