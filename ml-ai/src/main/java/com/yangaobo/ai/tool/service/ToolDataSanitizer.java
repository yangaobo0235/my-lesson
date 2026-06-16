package com.yangaobo.ai.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ToolDataSanitizer {

    private static final Set<String> SECRET_FIELDS = Set.of(
            "password",
            "internaltoken",
            "token",
            "authorization",
            "idcard",
            "orderowner");

    private final ObjectMapper objectMapper;

    public ToolDataSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode sanitize(Object value) {
        JsonNode tree = objectMapper.valueToTree(value);
        sanitizeNode(tree);
        return tree;
    }

    private void sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields =
                    objectNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey()
                        .replace("_", "")
                        .replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (SECRET_FIELDS.contains(normalized)) {
                    objectNode.put(field.getKey(), "***");
                } else {
                    sanitizeNode(field.getValue());
                }
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::sanitizeNode);
        }
    }
}
