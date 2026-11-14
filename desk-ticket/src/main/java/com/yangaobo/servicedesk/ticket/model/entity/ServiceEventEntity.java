package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目与资格变更审计的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "it_service_event", autoResultMap = true)
public class ServiceEventEntity implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 本地FK→it_service_item。 */
    private String serviceItemId;

    /** 资格事件必填，本地复合FK(entitlement_id,service_item_id)→it_service_entitlement(id,service_item_id)。 */
    private String entitlementId;

    /** 外部真实操作者账号。 */
    private String actorId;

    /**
     * SERVICE_CREATED / SERVICE_UPDATED / SERVICE_DISABLED / ENTITLEMENT_GRANTED /
     * ENTITLEMENT_UPDATED / ENTITLEMENT_SUSPENDED / ENTITLEMENT_RESUMED。
     */
    private String eventType;

    /** 操作者范围内管理动作幂等键。 */
    private String clientActionId;

    /** 动作、目标、期望版本及可写字段的规范化摘要。 */
    private String requestHash;

    /** schema_version、变更前后值/版本及非空原因，不含凭据。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode details;

    /** 业务变更提交时间。 */
    private java.time.LocalDateTime createdAt;
}
