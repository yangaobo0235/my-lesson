package com.yangaobo.servicedesk.knowledge.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 与Python一致的UTF-8规范JSON摘要，不包含传输或追踪字段。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class KnowledgeDigest {
    private final ObjectMapper json;
    /** 仅对已校验的业务模型生成摘要，浮点值不作为该合同输入。 */
    public String hash(Object value) {
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(json.writeValueAsBytes(sorted(json.valueToTree(value)))));}
        catch(java.io.IOException | java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("业务摘要生成失败",failure);}
    }
    /** 递归排序键；数组保留业务顺序，不改变值。 */
    private JsonNode sorted(JsonNode source) {
        if(source.isObject()) {
            ObjectNode result=json.createObjectNode();var keys=new java.util.ArrayList<String>();
            source.fieldNames().forEachRemaining(keys::add);keys.sort(String::compareTo);
            for(String key:keys)result.set(key,sorted(source.get(key)));return result;
        }
        if(source.isArray()) {ArrayNode result=json.createArrayNode();for(JsonNode item:source)result.add(sorted(item));return result;}
        return source;
    }
}
