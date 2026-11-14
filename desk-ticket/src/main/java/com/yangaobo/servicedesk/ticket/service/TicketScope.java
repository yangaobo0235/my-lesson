package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;

/**
 * 工单数据库授权范围的业务边界。
 *
 * @author yangaobo
 */
public final class TicketScope {
    /** 禁止实例化。 */
    private TicketScope() {}

    /** SQL侧限制客户本人或当前服务人员范围。 */
    public static LambdaQueryWrapper<TicketEntity> query(IdentityDTO actor) {
        LambdaQueryWrapper<TicketEntity> query = new LambdaQueryWrapper<>();
        if (actor.getRoles().contains("CUSTOMER_USER"))
            return query.eq(TicketEntity::getRequesterId, actor.getOwnerId())
                    .eq(TicketEntity::getCustomerId, actor.getOrganizationId());
        if (!(actor.getRoles().contains("SERVICE_AGENT")
                || actor.getRoles().contains("TECHNICIAN")))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        return actor.getCustomerIds().isEmpty()
                ? query.apply("1 = 0")
                : query.in(TicketEntity::getCustomerId, actor.getCustomerIds());
    }
}
