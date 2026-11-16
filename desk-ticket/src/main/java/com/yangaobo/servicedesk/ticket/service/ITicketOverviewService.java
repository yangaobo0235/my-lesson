package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketOverviewVO;

/**
 * 工单工作台聚合的业务边界。
 *
 * @author yangaobo
 */
public interface ITicketOverviewService {
    /**
     * 按当前身份范围在数据库侧聚合，关闭原因分别统计。
     */
    TicketOverviewVO overview(IdentityDTO actor);
}
