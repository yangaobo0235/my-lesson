package com.yangaobo.servicedesk.ticket.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/**
 * 动作语义摘要的业务边界。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
public class RequestDigest {
    private final ObjectMapper objectMapper;

    /** 固定动作、目标及规范化字段；对象键递归排序，数组顺序保留。 */
    public String hash(String action, String target, Object form) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("action", action);
        input.put("target", target);
        input.set("input", objectMapper.valueToTree(form));
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            canonical(input)
                                                    .toString()
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 排序只影响摘要，不修改原输入或事件内容。 */
    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeSet<String> keys = new TreeSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> sorted.set(key, canonical(node.get(key))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode values = objectMapper.createArrayNode();
            node.forEach(value -> values.add(canonical(value)));
            return values;
        }
        return node;
    }
}
