package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.NoArgsRequest;
import org.springframework.stereotype.Component;

@Component
public class UserTools {

    private final AiBusinessGateway businessGateway;

    public UserTools(AiBusinessGateway businessGateway) {
        this.businessGateway = businessGateway;
    }

    public UserAiClient.UserProfile getMyProfile(
            NoArgsRequest request) {
        return businessGateway.getMyProfile();
    }
}
