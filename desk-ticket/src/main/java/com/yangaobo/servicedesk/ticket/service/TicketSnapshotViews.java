package com.yangaobo.servicedesk.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.DeviceSnapshotVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.ServiceScopeSnapshotVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.SupportContextVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.SupportFieldSourcesVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketMetadataVO;

/**
 * 逐字段投影历史快照，新增持久字段不会自动进入浏览器。
 *
 * @author yangaobo
 */
public final class TicketSnapshotViews {
    /**
     * 禁止实例化无状态转换器。
     */
    private TicketSnapshotViews() {
    }

    /**
     * 已确认环境及逐字段来源；未知保持空值。
     */
    public static SupportContextVO context(JsonNode node) {
        if (node == null || node.isNull()) return null;
        SupportContextVO result = new SupportContextVO();
        result.setOsName(text(node, "os_name"));
        result.setOsVersion(text(node, "os_version"));
        result.setSoftwareName(text(node, "software_name"));
        result.setSoftwareVersion(text(node, "software_version"));
        result.setConnectionType(text(node, "connection_type"));
        result.setErrorCode(text(node, "error_code"));
        result.setAffectedScope(text(node, "affected_scope"));
        result.setFieldSources(sources(node.get("field_sources")));
        return result;
    }

    /**
     * 公开环境来源标签，不允许用户自行提交可信来源。
     */
    public static SupportFieldSourcesVO sources(JsonNode node) {
        if (node == null || node.isNull()) return null;
        SupportFieldSourcesVO result = new SupportFieldSourcesVO();
        result.setOsName(text(node, "os_name"));
        result.setOsVersion(text(node, "os_version"));
        result.setSoftwareName(text(node, "software_name"));
        result.setSoftwareVersion(text(node, "software_version"));
        result.setConnectionType(text(node, "connection_type"));
        result.setErrorCode(text(node, "error_code"));
        result.setAffectedScope(text(node, "affected_scope"));
        return result;
    }

    /**
     * 受理时服务范围的安全历史投影。
     */
    public static ServiceScopeSnapshotVO scope(JsonNode node) {
        if (node == null || node.isNull()) return null;
        ServiceScopeSnapshotVO result = new ServiceScopeSnapshotVO();
        result.setAdmissionType(text(node, "admission_type"));
        result.setEntitlementId(text(node, "entitlement_id"));
        result.setAdmissionEntitlementId(text(node, "admission_entitlement_id"));
        result.setQualificationVersion(text(node, "qualification_version"));
        result.setStartsOn(text(node, "starts_on"));
        result.setEndsOn(text(node, "ends_on"));
        result.setServiceItemId(text(node, "service_item_id"));
        result.setSupportScope(text(node, "support_scope"));
        result.setOnsiteSupported(
                node.hasNonNull("onsite_supported")
                        ? node.get("onsite_supported").booleanValue()
                        : null);
        return result;
    }

    /**
     * 受理时资产与型号的安全历史投影，不含序列号。
     */
    public static DeviceSnapshotVO device(JsonNode node) {
        if (node == null || node.isNull()) return null;
        DeviceSnapshotVO result = new DeviceSnapshotVO();
        result.setAssetCode(text(node, "asset_code"));
        result.setProductModelId(text(node, "product_model_id"));
        result.setBrand(text(node, "brand"));
        result.setModelName(text(node, "model_name"));
        return result;
    }

    /**
     * 工单事件可公开的结构化变更，不直出持久化快照。
     */
    public static TicketMetadataVO metadata(JsonNode node) {
        if (node == null || node.isNull()) return null;
        TicketMetadataVO result = new TicketMetadataVO();
        result.setAssigneeId(text(node, "assignee_id"));
        result.setPreviousAssigneeId(text(node, "previous_assignee_id"));
        result.setOldPriority(text(node, "old_priority"));
        result.setNewPriority(text(node, "new_priority"));
        result.setResolutionType(text(node, "resolution_type"));
        result.setPreviousResolutionType(text(node, "previous_resolution_type"));
        result.setReferralTarget(text(node, "referral_target"));
        result.setClosureReason(text(node, "closure_reason"));
        result.setOldScope(scope(node.get("old_scope")));
        result.setNewScope(scope(node.get("new_scope")));
        result.setOldContext(context(node.get("old_context")));
        result.setNewContext(context(node.get("new_context")));
        return result;
    }

    /**
     * 缺失或JSON null均保留未知，禁止默认成字符串null。
     */
    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
