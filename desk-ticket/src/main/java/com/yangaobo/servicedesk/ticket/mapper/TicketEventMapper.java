package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 不可变工单事件的业务边界。
 *
 * @author yangaobo
 */
public interface TicketEventMapper extends BaseMapper<TicketEventEntity> {
    /** 取得当前行锁并映射JSON字段。 */
    @Select(
            "SELECT id, ticket_id, sequence_number, event_type, onsite_booking_id, actor_type,"
                + " actor_id, client_action_id, source_draft_id, action_request_hash, visibility,"
                + " from_status, to_status, content, metadata, created_at FROM sd_ticket_event"
                + " WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_TicketEventEntity")
    TicketEventEntity lock(@Param("id") String id);

    /** 工单行锁串行化后分配下一序号。 */
    @Select(
            "SELECT COALESCE(MAX(sequence_number), 0) FROM sd_ticket_event WHERE ticket_id ="
                + " #{ticket}")
    int lastSequence(@Param("ticket") String ticket);
}
