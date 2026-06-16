package com.yangaobo.ai.knowledge.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Component
public class KnowledgeContentHasher {

    public String hash(
            String sourceType,
            String sourceId,
            String title,
            String content,
            String sourceUrl,
            Map<String, Object> metadata) {
        StringBuilder canonical = new StringBuilder()
                .append(sourceType).append('\n')
                .append(sourceId).append('\n')
                .append(nullToEmpty(title)).append('\n')
                .append(nullToEmpty(content)).append('\n')
                .append(nullToEmpty(sourceUrl)).append('\n');
        metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical
                        .append(entry.getKey())
                        .append('=')
                        .append(entry.getValue())
                        .append('\n'));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
