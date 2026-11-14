package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;

/**
 * 当前角色与客户范围授权的业务边界。
 *
 * @author yangaobo
 */
public final class SupportAuthorization {
    /** 禁止实例化无状态授权规则。 */
    private SupportAuthorization() {}

    /** 系统管理员只能执行明确管理动作，不因此获得工单正文。 */
    public static void administrator(IdentityDTO actor) {
        role(actor, "SYSTEM_ADMIN");
    }

    /** 固定角色动作校验。 */
    public static void role(IdentityDTO actor, String role) {
        if (actor.getMustChangePassword() || !actor.getRoles().contains(role))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    /** 客户只能使用本组织，服务人员须拥有明确当前范围。 */
    public static String customer(IdentityDTO actor, String requested) {
        if (actor.getRoles().contains("CUSTOMER_USER")) {
            if (requested != null && !requested.equals(actor.getOrganizationId()))
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            return actor.getOrganizationId();
        }
        if (!(actor.getRoles().contains("SERVICE_AGENT") || actor.getRoles().contains("TECHNICIAN"))
                || requested == null
                || !actor.getCustomerIds().contains(requested))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        return requested;
    }

    /** 客服维护客户资格与设备。 */
    public static void agent(IdentityDTO actor, String customerId) {
        role(actor, "SERVICE_AGENT");
        customer(actor, customerId);
    }

    /** 工单正文只按当前客户身份或服务角色及范围读取。 */
    public static boolean canRead(IdentityDTO actor, TicketEntity ticket) {
        if (actor.getRoles().contains("CUSTOMER_USER"))
            return actor.getOwnerId().equals(ticket.getRequesterId())
                    && actor.getOrganizationId().equals(ticket.getCustomerId());
        return (actor.getRoles().contains("SERVICE_AGENT")
                        || actor.getRoles().contains("TECHNICIAN"))
                && actor.getCustomerIds().contains(ticket.getCustomerId());
    }
}
