package com.yangaobo.servicedesk.ticket.model.vo.support;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目与资格变更审计的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ServiceEventVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键。")
    private String id;

    /** 本地FK→it_service_item。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地FK→it_service_item。")
    private String serviceItemId;

    /** 资格事件必填，本地复合FK(entitlement_id,service_item_id)→it_service_entitlement(id,service_item_id)。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "资格事件必填，本地复合FK(entitlement_id,service_item_id)→it_service_entitlement(id,service_item_id)。")
    private String entitlementId;

    /** 外部真实操作者账号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "外部真实操作者账号。")
    private String actorId;

    /**
     * SERVICE_CREATED / SERVICE_UPDATED / SERVICE_DISABLED / ENTITLEMENT_GRANTED /
     * ENTITLEMENT_UPDATED / ENTITLEMENT_SUSPENDED / ENTITLEMENT_RESUMED。
     */
    private String eventType;

    /** schema_version、变更前后值/版本及非空原因，不含凭据。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "schema_version、变更前后值/版本及非空原因，不含凭据。")
    private ServiceAuditDetailsVO details;

    /** 业务变更提交时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "业务变更提交时间。")
    private java.time.Instant createdAt;
}
