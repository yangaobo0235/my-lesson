package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户服务资格的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "it_service_entitlement", autoResultMap = true)
public class ServiceEntitlementEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，独立维护服务资格生命周期。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 外部CUSTOMER组织ID，客户创建后固定。
     */
    private String customerId;

    /**
     * 本地FK→it_service_item，创建后固定。
     */
    private String serviceItemId;

    /**
     * Asia/Shanghai起始日，含当日。
     */
    private LocalDate startsOn;

    /**
     * Asia/Shanghai截止日，含当日。
     */
    private LocalDate endsOn;

    /**
     * ACTIVE / SUSPENDED。
     */
    private String status;

    /**
     * 首次开通客服账号。
     */
    private String grantedBy;

    /**
     * 最后维护客服账号。
     */
    private String updatedBy;

    /**
     * 本次开通、续期或暂停原因，历史另记事件。
     */
    private String changeReason;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 显式维护更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 非负，比较后递增。
     */
    private Long rowVersion;
}
