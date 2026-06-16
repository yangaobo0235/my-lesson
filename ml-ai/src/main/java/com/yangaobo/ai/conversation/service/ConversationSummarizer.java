package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.conversation.model.ConversationMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationSummarizer {

    private static final String SYSTEM_PROMPT = """
            你负责压缩 MyLesson 助手的历史对话。
            摘要只用于理解用户偏好、代词指向、未完成问题和对话连续性。
            保留明确提到的课程名、文章名、公告名和用户约束。
            不得添加历史中没有的信息，不得把摘要描述为已经验证的业务事实。
            使用简洁中文，不超过 600 字。
            """;

    private final ChatClient chatClient;

    public ConversationSummarizer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String summarize(
            String existingSummary,
            List<ConversationMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            prompt.append("已有摘要：\n")
                    .append(existingSummary)
                    .append("\n\n");
        }
        prompt.append("需要合并的历史消息：\n");
        for (ConversationMessage message : messages) {
            prompt.append(message.role())
                    .append("：")
                    .append(message.content())
                    .append('\n');
        }
        String summary = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt.toString())
                .call()
                .content();
        if (summary == null || summary.isBlank()) {
            throw new IllegalStateException("Conversation summary is empty");
        }
        return summary.trim();
    }
}
