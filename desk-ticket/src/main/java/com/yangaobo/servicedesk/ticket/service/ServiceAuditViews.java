package com.yangaobo.servicedesk.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceAuditDetailsVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceAuditStateVO;

/**
 * 从已保存审计逐字段投影浏览器合同，不直接返回数据库JSON。
 *
 * @author yangaobo
 */
public final class ServiceAuditViews {
    /** 无状态转换器不实例化。 */
    private ServiceAuditViews() {}

    /** 保留创建前空状态及明确变更原因。 */
    public static ServiceAuditDetailsVO details(JsonNode node) {
        ServiceAuditDetailsVO result = new ServiceAuditDetailsVO();
        result.setBefore(state(node.get("before")));
        result.setAfter(state(node.get("after")));
        result.setReason(node.path("reason").asText());
        return result;
    }

    /** 仅公开当前合同列出的历史字段，兼容已有快照大小写。 */
    private static ServiceAuditStateVO state(JsonNode node) {
        if (node == null || node.isNull()) return null;
        ServiceAuditStateVO result = new ServiceAuditStateVO();
        result.setId(field(node, "id", "id").asText(null));
        result.setCustomerId(field(node, "customerId", "customer_id").asText(null));
        result.setServiceItemId(field(node, "serviceItemId", "service_item_id").asText(null));
        result.setServiceCode(field(node, "serviceCode", "service_code").asText(null));
        result.setCategory(field(node, "category", "category").asText(null));
        result.setServiceName(field(node, "serviceName", "service_name").asText(null));
        result.setSupportScope(field(node, "supportScope", "support_scope").asText(null));
        result.setStatus(field(node, "status", "status").asText(null));
        result.setChangeReason(field(node, "changeReason", "change_reason").asText(null));
        result.setRowVersion(field(node, "rowVersion", "row_version").asText(null));
        result.setStartsOn(field(node, "startsOn", "starts_on").asText(null));
        result.setEndsOn(field(node, "endsOn", "ends_on").asText(null));
        result.setCreatedAt(field(node, "createdAt", "created_at").asText(null));
        result.setUpdatedAt(field(node, "updatedAt", "updated_at").asText(null));
        result.setOnsiteSupported(
                field(node, "onsiteSupported", "onsite_supported").isNull()
                        ? null
                        : field(node, "onsiteSupported", "onsite_supported").booleanValue());
        return result;
    }

    /** 只在已知历史字段名间转换，不递归输出未知属性。 */
    private static JsonNode field(JsonNode node, String camel, String snake) {
        JsonNode value = node.has(camel) ? node.get(camel) : node.get(snake);
        return value == null ? com.fasterxml.jackson.databind.node.NullNode.instance : value;
    }
}
