package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;

/**
 * 工单读取角色的服务边界；客户范围由各查询的XML条件继续限定。
 *
 * @author yangaobo
 */
public final class TicketScope {
    /**
     * 禁止实例化。
     */
    private TicketScope() {
    }

    /**
     * 验证客户或服务团队读取角色，不将角色核验当作资源授权。
     *
     * @param actor 已验证的当前身份
     */
    public static void requireReader(IdentityDTO actor) {
        if (!(actor.getRoles().contains("CUSTOMER_USER")
                || actor.getRoles().contains("SERVICE_AGENT")
                || actor.getRoles().contains("TECHNICIAN")))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
}
