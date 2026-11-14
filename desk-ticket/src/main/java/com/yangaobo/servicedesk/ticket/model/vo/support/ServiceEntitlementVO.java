package com.yangaobo.servicedesk.ticket.model.vo.support;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户服务资格的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ServiceEntitlementVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键，独立维护服务资格生命周期。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键，独立维护服务资格生命周期。")
    private String id;

    /** 外部CUSTOMER组织ID，客户创建后固定。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "外部CUSTOMER组织ID，客户创建后固定。")
    private String customerId;

    /** 本地FK→it_service_item，创建后固定。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地FK→it_service_item，创建后固定。")
    private String serviceItemId;

    /** Asia/Shanghai起始日，含当日。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "Asia/Shanghai起始日，含当日。")
    private java.time.LocalDate startsOn;

    /** Asia/Shanghai截止日，含当日。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "Asia/Shanghai截止日，含当日。")
    private java.time.LocalDate endsOn;

    /** ACTIVE / SUSPENDED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "ACTIVE / SUSPENDED。")
    private String status;

    /** 本次开通、续期或暂停原因，历史另记事件。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本次开通、续期或暂停原因，历史另记事件。")
    private String changeReason;

    /** 创建时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "创建时间。")
    private java.time.Instant createdAt;

    /** 显式维护更新时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "显式维护更新时间。")
    private java.time.Instant updatedAt;

    /** 非负，比较后递增。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "非负，比较后递增。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
