package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.UserAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class LearningPlanQueryRewriter {

    private static final Logger log =
            LoggerFactory.getLogger(LearningPlanQueryRewriter.class);

    private final ChatClient chatClient;

    public LearningPlanQueryRewriter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String rewrite(String goal, UserAiClient.UserProfile profile) {
        try {
            String rewritten = chatClient.prompt()
                    .system("""
                            你是学习课程检索词改写器。只输出一个简短检索词，
                            不得改变学习目标，不得加入用户未表达的领域，最多 30 个字符。
                            """)
                    .user("目标：" + goal + "\n画像：" + profileText(profile))
                    .call()
                    .content();
            String normalized = normalize(rewritten);
            if (!normalized.isBlank()) {
                return normalized;
            }
        } catch (RuntimeException exception) {
            log.warn("Learning plan query rewrite failed: {}",
                    exception.getClass().getSimpleName());
        }
        return normalize(goal.replaceAll(
                "学习计划|学习|入门|课程|计划|掌握|提升|基础", " "));
    }

    private String profileText(UserAiClient.UserProfile profile) {
        return profile == null ? "unknown" : String.valueOf(profile.info());
    }

    private String normalize(String value) {
        String normalized = value == null
                ? ""
                : value.replaceAll("[\\r\\n]+", " ")
                        .replaceAll("[\"'`]+", "")
                        .replaceAll("\\s+", " ")
                        .trim();
        return normalized.length() <= 30
                ? normalized
                : normalized.substring(0, 30);
    }
}
