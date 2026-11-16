package com.yangaobo.servicedesk.ticket.model.dto.support;

import com.yangaobo.servicedesk.ticket.model.entity.ServiceEntitlementEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务受理资格决策的业务边界。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ServiceAdmission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 被锁定并核验的项目。
     */
    private ServiceItemEntity item;

    /**
     * 被锁定并核验的客户资格。
     */
    private ServiceEntitlementEntity entitlement;

    /**
     * 取得锁之后查询的数据库UTC时间。
     */
    private LocalDateTime decisionTime;
}
