package com.yangaobo.ai.conversation.model;

import java.util.List;

public record ConversationMemoryContext(
        String summary,
        List<ConversationMessage> recentMessages
) {

    public String promptContext() {
        StringBuilder result = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            result.append("历史摘要：\n")
                    .append(summary)
                    .append("\n\n");
        }
        if (!recentMessages.isEmpty()) {
            result.append("最近对话：\n");
            for (ConversationMessage message : recentMessages) {
                result.append(message.role())
                        .append("：")
                        .append(message.content())
                        .append('\n');
            }
        }
        return result.toString().trim();
    }

    public String retrievalQuery(String currentQuestion) {
        List<String> recentUserMessages = recentMessages.stream()
                .filter(message -> "USER".equals(message.role()))
                .map(ConversationMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        int start = Math.max(0, recentUserMessages.size() - 2);
        if (start == recentUserMessages.size()) {
            return currentQuestion;
        }
        String previous = String.join(
                "；",
                recentUserMessages.subList(start, recentUserMessages.size()));
        return currentQuestion + "\n对话上文：" + previous;
    }
}
