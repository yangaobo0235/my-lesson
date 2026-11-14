package com.yangaobo.servicedesk.ticket.mapper;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketOverviewVO;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工单工作台聚合的业务边界。
 *
 * @author yangaobo
 */
public interface TicketOverviewMapper {
    /** 同一授权条件下聚合状态及关闭原因，不截取列表后统计。 */
    @Select(
            """
<script>SELECT
    COALESCE(SUM(status = 'SUBMITTED'), 0) submitted,
    COALESCE(SUM(status = 'PROCESSING'), 0) processing,
    COALESCE(SUM(status = 'WAITING_CUSTOMER'), 0) waiting_customer,
    COALESCE(SUM(status = 'RESOLVED'), 0) resolved,
    COALESCE(SUM(status = 'SUBMITTED' AND acceptance_due_at &lt; UTC_TIMESTAMP(6)), 0) overdue,
    COALESCE(SUM(service_item_id IS NULL AND status IN ('SUBMITTED','PROCESSING','WAITING_CUSTOMER')), 0) triage,
    COALESCE(SUM(closure_reason = 'CUSTOMER_CONFIRMED'), 0) customer_confirmed,
    COALESCE(SUM(closure_reason = 'CONFIRMATION_TIMEOUT'), 0) confirmation_timeout
FROM sd_ticket WHERE
<choose><when test="customer">requester_id = #{actor.ownerId} AND customer_id = #{actor.organizationId}</when>
<when test="actor.customerIds.size() > 0">customer_id IN
    <foreach collection="actor.customerIds" item="id" open="(" close=")" separator=",">#{id}</foreach></when>
<otherwise>1 = 0</otherwise></choose></script>
""")
    TicketOverviewVO overview(
            @Param("actor") IdentityDTO actor, @Param("customer") boolean customer);
}
