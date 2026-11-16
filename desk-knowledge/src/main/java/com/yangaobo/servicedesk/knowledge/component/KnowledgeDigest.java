package com.yangaobo.servicedesk.knowledge.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 与Python一致的UTF-8规范JSON摘要，不包含传输或追踪字段。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDigest {
    private final ObjectMapper json;

    /**
     * 仅对已校验的业务模型生成摘要，浮点值不作为该合同输入。
     */
    public String hash(Object value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            json.writeValueAsBytes(
                                                    sorted(json.valueToTree(value)))));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("业务摘要生成失败", failure);
        }
    }

    /**
     * 递归排序键；数组保留业务顺序，不改变值。
     */
    private JsonNode sorted(JsonNode source) {
        if (source.isObject()) {
            ObjectNode result = json.createObjectNode();
            var keys = new ArrayList<String>();
            source.fieldNames().forEachRemaining(keys::add);
            keys.sort(String::compareTo);
            for (String key : keys) result.set(key, sorted(source.get(key)));
            return result;
        }
        if (source.isArray()) {
            ArrayNode result = json.createArrayNode();
            for (JsonNode item : source) result.add(sorted(item));
            return result;
        }
        return source;
    }
}
