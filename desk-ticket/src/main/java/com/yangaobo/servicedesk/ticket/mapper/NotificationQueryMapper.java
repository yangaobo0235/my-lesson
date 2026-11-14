package com.yangaobo.servicedesk.ticket.mapper;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.NotificationVO;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 有权通知分页查询的业务边界。
 *
 * @author yangaobo
 */
public interface NotificationQueryMapper {
    /** 当前有权通知的数据库分页，不返回撤权资源的标题。 */
    @Select(
            """
<script>SELECT n.id, n.ticket_id, n.event_id, n.created_at, n.read_at, t.title, e.event_type
FROM sd_notification n JOIN sd_ticket t ON t.id = n.ticket_id
JOIN sd_ticket_event e ON e.id = n.event_id WHERE n.recipient_id = #{actor.ownerId} AND
<choose><when test="customer">t.requester_id = #{actor.ownerId} AND t.customer_id = #{actor.organizationId} AND e.visibility = 'PUBLIC'</when>
<when test="actor.customerIds.size() > 0">t.customer_id IN
<foreach collection="actor.customerIds" item="id" open="(" close=")" separator=",">#{id}</foreach></when>
<otherwise>1 = 0</otherwise></choose>
ORDER BY n.created_at DESC, n.id LIMIT #{limit} OFFSET #{offset}</script>
""")
    List<NotificationVO> list(
            @Param("actor") IdentityDTO actor,
            @Param("customer") boolean customer,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 与列表相同授权条件的总数。 */
    @Select(
            """
<script>SELECT COUNT(*) FROM sd_notification n JOIN sd_ticket t ON t.id = n.ticket_id
JOIN sd_ticket_event e ON e.id = n.event_id WHERE n.recipient_id = #{actor.ownerId} AND
<choose><when test="customer">t.requester_id = #{actor.ownerId} AND t.customer_id = #{actor.organizationId} AND e.visibility = 'PUBLIC'</when>
<when test="actor.customerIds.size() > 0">t.customer_id IN
<foreach collection="actor.customerIds" item="id" open="(" close=")" separator=",">#{id}</foreach></when>
<otherwise>1 = 0</otherwise></choose></script>
""")
    long count(@Param("actor") IdentityDTO actor, @Param("customer") boolean customer);
}
