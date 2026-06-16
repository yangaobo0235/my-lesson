package com.yangaobo.ai.observability;

import com.yangaobo.ai.conversation.model.ConversationRun;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AiLogContext {

    private static final String[] KEYS = {
            "traceId", "runId", "conversationId", "userIdHash",
            "agentName", "toolName"
    };

    private AiLogContext() {
    }

    public static void open(Long userId, ConversationRun run) {
        MDC.put("traceId", run.traceId());
        MDC.put("runId", run.id().toString());
        MDC.put("conversationId", run.conversationId().toString());
        MDC.put("userIdHash", hash(userId));
        MDC.put("agentName", "mylesson_assistant");
    }

    public static void tool(String toolName) {
        MDC.put("toolName", toolName);
    }

    public static void clearTool() {
        MDC.remove("toolName");
    }

    public static void close() {
        for (String key : KEYS) {
            MDC.remove(key);
        }
    }

    private static String hash(Long userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(userId)
                            .getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
