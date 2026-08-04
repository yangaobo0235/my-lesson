package com.yangaobo.feign;

import com.yangaobo.dto.ai.InternalAiResponse;
import com.yangaobo.dto.ai.UserProfileAiDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "ml-user", contextId = "orderUserAiInternalFeign")
public interface UserAiInternalFeign {

    @GetMapping("/internal/ai/users/{userId}/profile")
    InternalAiResponse<UserProfileAiDTO> profile(
            @PathVariable("userId") Long userId);
}
