package com.yangaobo.servicedesk.ticket.mapper;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketOverviewVO;
import org.apache.ibatis.annotations.Param;

/**
 * 工单工作台聚合的业务边界。
 *
 * @author yangaobo
 */
public interface TicketOverviewMapper {
    /**
     * 同一授权条件下聚合状态及关闭原因，不截取列表后统计。
     */
    TicketOverviewVO overview(
            @Param("actor") IdentityDTO actor, @Param("customer") boolean customer);
}
