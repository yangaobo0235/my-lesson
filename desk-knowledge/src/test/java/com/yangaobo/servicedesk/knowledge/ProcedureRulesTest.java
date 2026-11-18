package com.yangaobo.servicedesk.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.service.ProcedureRules;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentUploadForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 规程禁止脚本、越界定位、循环前置和客户越权操作。
 *
 * @author yangaobo
 */
class ProcedureRulesTest {
    /**
     * 执行过前置不等于条件满足，审核定义必须固定允许的原选择题答案。
     */
    @Test
    void validatesPrerequisiteAnswerValues() throws Exception {
        var value = definition();
        var checks = (com.fasterxml.jackson.databind.node.ArrayNode) value.path("checks");
        var prior = (ObjectNode) checks.get(0);
        var answer = (ObjectNode) prior.path("answerSchema");
        answer.put("kind", "CHOICE");
        answer.putArray("options").add("允许自行调整").add("提示无权访问");
        var next = prior.deepCopy();
        next.put("checkKey", "recover");
        next.putArray("prerequisiteCheckKeys").add("observe_connection");
        var condition = next.putArray("prerequisiteAnswers").addObject();
        condition.put("checkKey", "observe_connection");
        condition.putArray("allowedValues").add("允许自行调整");
        checks.add(next);
        assertDoesNotThrow(() -> ProcedureRules.validate(value, "MARKDOWN"));
        condition.putArray("allowedValues").add("虚构答案");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
        condition.putArray("allowedValues").add("允许自行调整");
        condition.put("checkKey", "recover");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
        condition.put("checkKey", "observe_connection");
        answer.put("kind", "TEXT");
        answer.putArray("options");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
    }

    /**
     * 浏览器显式null与省略可选规程均能上传普通资料，真实定义仍完整保留。
     */
    @Test
    void normalizesOptionalProcedureAtJsonBoundary() throws Exception {
        var mapper = new ObjectMapper();
        assertNull(mapper.readValue("{}", DocumentUploadForm.class).getProcedureDefinition());
        assertNull(mapper.readValue("{\"procedureDefinition\":null}", DocumentUploadForm.class).getProcedureDefinition());
        var expected = definition();
        var body = mapper.createObjectNode().set("procedureDefinition", expected);
        assertEquals(expected, mapper.treeToValue(body, DocumentUploadForm.class).getProcedureDefinition());
    }

    /**
     * 独立输入避免从被测实现生成预期规则。
     */
    private ObjectNode definition() throws Exception {
        return (ObjectNode) new ObjectMapper().readTree("""
                {"schemaVersion":1,"applicability":"Windows无线连接问题",
                 "requiredFields":["os_name"],"stopConditions":["SAFETY_HAZARD_REPORTED"],
                 "handoverConditions":["ADMIN_PRIVILEGE_REQUIRED"],"checks":[{
                   "checkKey":"observe_connection","roles":["CUSTOMER","TECHNICIAN"],
                   "instruction":"查看当前无线连接状态，不更改设置。",
                   "answerSchema":{"schemaVersion":1,"kind":"TEXT","options":[],"maxLength":2000},
                   "requiredFields":[],"prerequisiteCheckKeys":[],"riskLevel":"LOW",
                   "stopConditions":[],"sourceLocator":{"kind":"MARKDOWN","anchor":"连接状态","lineStart":2,"lineEnd":4}
                 }]}
                """);
    }

    /**
     * 正常只读观察规程保留明确原文定位。
     */
    @Test
    void acceptsReviewedObservation() throws Exception {
        var value = definition();
        assertDoesNotThrow(() -> ProcedureRules.validate(value, "MARKDOWN"));
        assertDoesNotThrow(() -> ProcedureRules.verifyLocations(value, "MARKDOWN", 4));
        assertThrows(BusinessException.class, () -> ProcedureRules.verifyLocations(value, "MARKDOWN", 3));
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "PDF"));
    }

    /**
     * 任意执行字段、循环前置和原件外定位必须失败。
     */
    @Test
    void rejectsExecutableOrForwardReferences() throws Exception {
        var value = definition();
        var check = (ObjectNode) value.path("checks").get(0);
        check.put("script", "echo unsafe");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
        check.remove("script");
        check.putArray("prerequisiteCheckKeys").add("observe_connection");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
    }

    /**
     * 版本与定位不能借字符串强转或低32位截断伪装合法数值。
     */
    @Test
    void rejectsIntegerCoercionAndOverflow() throws Exception {
        var value = definition();
        value.put("schemaVersion", 4294967297L);
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
        value.put("schemaVersion", 1);
        var check = (ObjectNode) value.path("checks").get(0);
        ((ObjectNode) check.path("answerSchema")).put("schemaVersion", "1");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
        ((ObjectNode) check.path("answerSchema")).put("schemaVersion", 1);
        ((ObjectNode) check.path("sourceLocator")).put("lineStart", 4294967298L);
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
    }

    /**
     * 高风险操作不能伪装成客户自助步骤。
     */
    @Test
    void rejectsCustomerHandoverOperationAndInvalidAnswers() throws Exception {
        var value = definition();
        var check = (ObjectNode) value.path("checks").get(0);
        check.put("riskLevel", "HANDOVER_REQUIRED");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
        check.put("riskLevel", "LOW");
        ((ObjectNode) check.path("answerSchema")).put("kind", "CHOICE");
        assertThrows(BusinessException.class, () -> ProcedureRules.validate(value, "MARKDOWN"));
    }
}
