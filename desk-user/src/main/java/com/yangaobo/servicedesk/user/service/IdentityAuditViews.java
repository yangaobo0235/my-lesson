package com.yangaobo.servicedesk.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yangaobo.servicedesk.user.model.vo.account.IdentityAuditDetailsVO;
import com.yangaobo.servicedesk.user.model.vo.account.IdentityAuditStateVO;

/**
 * 从已保存审计逐字段投影浏览器合同，不直接返回数据库JSON。
 *
 * @author yangaobo
 */
public final class IdentityAuditViews {
    /** 无状态转换器不实例化。 */
    private IdentityAuditViews() {}

    /** 保留创建前空状态及明确变更原因。 */
    public static IdentityAuditDetailsVO details(JsonNode node) {
        IdentityAuditDetailsVO result = new IdentityAuditDetailsVO();
        result.setBefore(state(node.get("before")));
        result.setAfter(state(node.get("after")));
        result.setReason(node.path("reason").asText());
        return result;
    }

    /** 仅公开当前合同列出的历史字段，兼容已有快照大小写。 */
    private static IdentityAuditStateVO state(JsonNode node) {
        if (node == null || node.isNull()) return null;
        IdentityAuditStateVO result = new IdentityAuditStateVO();
        if (node.isTextual()) {
            result.setBootstrapName(node.asText());
            return result;
        }
        result.setId(field(node, "id", "id").asText(null));
        result.setOrganizationId(field(node, "organizationId", "organization_id").asText(null));
        result.setOrganizationType(
                field(node, "organizationType", "organization_type").asText(null));
        result.setOrganizationCode(
                field(node, "organizationCode", "organization_code").asText(null));
        result.setOrganizationName(
                field(node, "organizationName", "organization_name").asText(null));
        result.setUsername(field(node, "username", "username").asText(null));
        result.setDisplayName(field(node, "displayName", "display_name").asText(null));
        result.setStatus(field(node, "status", "status").asText(null));
        result.setRowVersion(field(node, "rowVersion", "row_version").asText(null));
        result.setCreatedAt(field(node, "createdAt", "created_at").asText(null));
        result.setUpdatedAt(field(node, "updatedAt", "updated_at").asText(null));
        result.setMustChangePassword(
                field(node, "mustChangePassword", "must_change_password").isNull()
                        ? null
                        : field(node, "mustChangePassword", "must_change_password").booleanValue());
        result.setRoles(
                field(node, "roles", "roles").isArray()
                        ? java.util.stream.StreamSupport.stream(
                                        field(node, "roles", "roles").spliterator(), false)
                                .map(JsonNode::asText)
                                .toList()
                        : null);
        result.setCustomerIds(
                field(node, "customerIds", "customer_ids").isArray()
                        ? java.util.stream.StreamSupport.stream(
                                        field(node, "customerIds", "customer_ids").spliterator(),
                                        false)
                                .map(JsonNode::asText)
                                .toList()
                        : null);
        return result;
    }

    /** 只在已知历史字段名间转换，不递归输出未知属性。 */
    private static JsonNode field(JsonNode node, String camel, String snake) {
        JsonNode value = node.has(camel) ? node.get(camel) : node.get(snake);
        return value == null ? com.fasterxml.jackson.databind.node.NullNode.instance : value;
    }
}
