package com.yangaobo.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class RagQueryRewriter {

    private static final Logger log =
            LoggerFactory.getLogger(RagQueryRewriter.class);
    private final ChatClient chatClient;

    public RagQueryRewriter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String rewrite(String query) {
        if (query == null || query.strip().length() < 4) {
            return "";
        }
        try {
            String result = chatClient.prompt()
                    .system("""
                            你是知识检索词改写器。仅消除口语和代词，保留原意，
                            不增加主题或事实，只输出一条不超过 50 字的检索语句。
                            """)
                    .user(query)
                    .call()
                    .content();
            String normalized = result == null ? "" : result
                    .replaceAll("[\\r\\n]+", " ")
                    .replaceAll("[\"'`]+", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            return normalized.length() <= 50
                    ? normalized
                    : normalized.substring(0, 50);
        } catch (RuntimeException exception) {
            log.warn("RAG query rewrite failed: {}",
                    exception.getClass().getSimpleName());
            return "";
        }
    }
}
