package com.yangaobo.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 杨奥博
 */
@Profile("dev")
@RestController
@RequestMapping("/internal/smoke")
public class AiSmokeController {

    private final ChatClient chatClient;

    public AiSmokeController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam(defaultValue = "只回复 pong") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}