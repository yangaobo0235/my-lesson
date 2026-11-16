package com.yangaobo.servicedesk.ticket.model.vo.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目与资格变更审计的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ServiceEventVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @Schema(description = "主键。")
    private String id;

    /**
     * 本地FK→it_service_item。
     */
    @Schema(description = "本地FK→it_service_item。")
    private String serviceItemId;

    /**
     * 资格事件必填，本地复合FK(entitlement_id,service_item_id)→it_service_entitlement(id,service_item_id)。
     */
    @Schema(
            description =
                    "资格事件必填，本地复合FK(entitlement_id,service_item_id)→it_service_entitlement(id,service_item_id)。")
    private String entitlementId;

    /**
     * 外部真实操作者账号。
     */
    @Schema(description = "外部真实操作者账号。")
    private String actorId;

    /**
     * SERVICE_CREATED / SERVICE_UPDATED / SERVICE_DISABLED / ENTITLEMENT_GRANTED /
     * ENTITLEMENT_UPDATED / ENTITLEMENT_SUSPENDED / ENTITLEMENT_RESUMED。
     */
    private String eventType;

    /**
     * schema_version、变更前后值/版本及非空原因，不含凭据。
     */
    @Schema(description = "schema_version、变更前后值/版本及非空原因，不含凭据。")
    private ServiceAuditDetailsVO details;

    /**
     * 业务变更提交时间。
     */
    @Schema(description = "业务变更提交时间。")
    private Instant createdAt;
}
