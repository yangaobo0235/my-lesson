package com.yangaobo.servicedesk.ticket.service.impl;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.mapper.TicketOverviewMapper;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketOverviewVO;
import com.yangaobo.servicedesk.ticket.service.ITicketOverviewService;
import com.yangaobo.servicedesk.ticket.service.TicketScope;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 工单工作台聚合的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketOverviewServiceImpl implements ITicketOverviewService {
    private final TicketOverviewMapper overviewMapper;

    /** {@inheritDoc} 概况权限不授予任何工单正文。 */
    @Override
    public TicketOverviewVO overview(IdentityDTO actor) {
        TicketScope.query(actor);
        return overviewMapper.overview(actor, actor.getRoles().contains("CUSTOMER_USER"));
    }
}
