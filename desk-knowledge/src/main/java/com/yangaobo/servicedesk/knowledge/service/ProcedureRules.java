package com.yangaobo.servicedesk.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 修订内审核规程的有限数据合同，禁止表达式、跳转和动态脚本。
 *
 * @author yangaobo
 */
public final class ProcedureRules {
    /**
     * 宿主能够来源化确认的环境字段。
     */
    private static final Set<String> FIELDS = Set.of("os_name", "os_version", "software_name",
            "software_version", "connection_type", "affected_scope", "error_code");
    /**
     * 仅由宿主或明确人工反馈判定的停止条件。
     */
    private static final Set<String> CONDITIONS = Set.of("SAFETY_HAZARD_REPORTED",
            "DATA_LOSS_RISK_REPORTED", "ADMIN_PRIVILEGE_REQUIRED", "OUT_OF_SERVICE_SCOPE",
            "NO_PROGRESS_LIMIT", "EVIDENCE_CONFLICT");

    /**
     * 规则不持有外部资源或可变状态。
     */
    private ProcedureRules() {
    }

    /**
     * 校验不可变定义；未知字段和向前引用均明确拒绝，不静默裁剪输入。
     */
    public static void validate(JsonNode definition, String format) {
        require(definition != null && definition.toString().getBytes(StandardCharsets.UTF_8).length <= 65536);
        object(definition, Set.of("schemaVersion", "applicability", "requiredFields", "checks",
                "stopConditions", "handoverConditions"));
        require(integer(definition.path("schemaVersion")) == 1);
        string(definition.path("applicability"), 2000);
        strings(definition.path("requiredFields"), FIELDS, 7);
        strings(definition.path("stopConditions"), CONDITIONS, 10);
        strings(definition.path("handoverConditions"), CONDITIONS, 10);
        JsonNode checks = definition.path("checks");
        require(checks.isArray() && !checks.isEmpty() && checks.size() <= 20);
        Set<String> earlier = new HashSet<>();
        Map<String, JsonNode> definitions = new HashMap<>();
        for (JsonNode check : checks) {
            object(check, Set.of("checkKey", "roles", "instruction", "answerSchema", "requiredFields",
                    "prerequisiteCheckKeys", "prerequisiteAnswers", "riskLevel", "stopConditions", "sourceLocator"));
            String key = string(check.path("checkKey"), 64);
            require(key.matches("[a-z][a-z0-9_]{0,63}"));
            Set<String> prerequisites = strings(check.path("prerequisiteCheckKeys"), earlier, 20);
            prerequisiteAnswers(check.path("prerequisiteAnswers"), prerequisites, definitions);
            require(earlier.add(key));
            Set<String> roles = strings(check.path("roles"), Set.of("CUSTOMER", "TECHNICIAN"), 2);
            require(!roles.isEmpty());
            String risk = string(check.path("riskLevel"), 32);
            require(Set.of("LOW", "HANDOVER_REQUIRED").contains(risk));
            require(!"HANDOVER_REQUIRED".equals(risk) || !roles.contains("CUSTOMER"));
            string(check.path("instruction"), 4000);
            strings(check.path("requiredFields"), FIELDS, 7);
            strings(check.path("stopConditions"), CONDITIONS, 10);
            answer(check.path("answerSchema"));
            locator(check.path("sourceLocator"), format, Integer.MAX_VALUE);
            definitions.put(key, check);
        }
    }

    /**
     * 前置执行与答案满足分别校验；只允许引用较早选择题的有限值集合。
     */
    private static void prerequisiteAnswers(JsonNode value, Set<String> prerequisites, Map<String, JsonNode> earlier) {
        if (value.isMissingNode()) return;
        require(value.isArray() && value.size() <= 20);
        Set<String> used = new HashSet<>();
        for (JsonNode condition : value) {
            object(condition, Set.of("checkKey", "allowedValues"));
            String key = string(condition.path("checkKey"), 64);
            require(prerequisites.contains(key) && used.add(key));
            JsonNode prior = earlier.get(key);
            require(prior != null && "CHOICE".equals(prior.path("answerSchema").path("kind").asText()));
            Set<String> options = new HashSet<>();
            prior.path("answerSchema").path("options").forEach(option -> options.add(option.textValue()));
            JsonNode values = condition.path("allowedValues");
            require(values.isArray() && !values.isEmpty() && values.size() <= 20);
            Set<String> accepted = new HashSet<>();
            for (JsonNode allowed : values) {
                String text = string(allowed, 200);
                require(options.contains(text) && accepted.add(text));
            }
        }
    }

    /**
     * 原件页数或真实行数已核实后，逐项验证定位，禁止指向原件之外。
     */
    public static void verifyLocations(JsonNode definition, String format, int extent) {
        require(extent > 0);
        for (JsonNode check : definition.path("checks")) locator(check.path("sourceLocator"), format, extent);
    }

    /**
     * 有限答案结构与AI的AnswerSchema保持一致，不接收任意JSON Schema。
     */
    private static void answer(JsonNode value) {
        object(value, Set.of("schemaVersion", "kind", "options", "minimum", "maximum", "maxLength"));
        require(integer(value.path("schemaVersion")) == 1);
        String kind = string(value.path("kind"), 16);
        require(Set.of("TEXT", "CHOICE", "BOOLEAN", "NUMBER").contains(kind));
        JsonNode options = value.path("options");
        require(options.isArray() && options.size() <= 20);
        Set<String> unique = new HashSet<>();
        for (JsonNode option : options) require(unique.add(string(option, 200)));
        require("CHOICE".equals(kind) == !unique.isEmpty());
        JsonNode minimum = value.path("minimum"), maximum = value.path("maximum");
        for (JsonNode bound : new JsonNode[]{minimum, maximum})
            require(bound.isMissingNode() || bound.isNull() || ("NUMBER".equals(kind) && bound.isNumber()
                    && Double.isFinite(bound.doubleValue())));
        if (minimum.isNumber() && maximum.isNumber())
            require(minimum.decimalValue().compareTo(maximum.decimalValue()) <= 0);
        int maxLength = integer(value.path("maxLength"));
        require(maxLength >= 1 && maxLength <= 2000);
    }

    /**
     * 定位保留格式专属含义，Markdown不接受伪页码。
     */
    private static void locator(JsonNode value, String format, int extent) {
        boolean pdf = "PDF".equals(format);
        object(value, pdf ? Set.of("kind", "pageNumber") : Set.of("kind", "anchor", "lineStart", "lineEnd"));
        require(format.equals(value.path("kind").asText()));
        if (pdf) {
            int page = integer(value.path("pageNumber"));
            require(page >= 1 && page <= extent);
        } else {
            string(value.path("anchor"), 200);
            int start = integer(value.path("lineStart")), end = integer(value.path("lineEnd"));
            require(start >= 1 && end >= start && end <= extent);
        }
    }

    /**
     * 对象只允许当前合同登记的字段。
     */
    private static void object(JsonNode value, Set<String> keys) {
        require(value.isObject());
        value.fieldNames().forEachRemaining(key -> require(keys.contains(key)));
    }

    /**
     * 拒绝字符串强转、小数和整数截断，避免伪造合法定位或结构版本。
     */
    private static int integer(JsonNode value) {
        require(value.isIntegralNumber() && value.canConvertToInt());
        return value.intValue();
    }

    /**
     * 列表值必须来自宿主白名单且不能重复。
     */
    private static Set<String> strings(JsonNode value, Set<String> allowed, int maximum) {
        require(value.isArray() && value.size() <= maximum);
        Set<String> seen = new HashSet<>();
        for (JsonNode item : value) {
            String text = string(item, 64);
            require(allowed.contains(text) && seen.add(text));
        }
        return seen;
    }

    /**
     * 文本只校验，不替调用者改写不可变定义。
     */
    private static String string(JsonNode value, int maximum) {
        require(value.isTextual() && !value.textValue().isBlank() && value.textValue().length() <= maximum);
        return value.textValue();
    }

    /**
     * 返回安全业务错误，原始规程正文不进入异常信息。
     */
    private static void require(boolean valid) {
        if (!valid) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
