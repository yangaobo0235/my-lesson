package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 工单的业务边界。
 *
 * @author yangaobo
 */
public interface TicketMapper extends BaseMapper<TicketEntity> {
    /** 取得当前行锁并映射JSON字段。 */
    @Select(
            "SELECT id, ticket_no, customer_id, requester_id, client_request_id, request_hash,"
                + " source_draft_id, service_item_id, entitlement_id, service_scope_snapshot,"
                + " device_id, device_snapshot, support_context, handover_snapshot,"
                + " service_area_code, service_location, title, symptom, attempted_actions,"
                + " priority, status, assignee_id, first_accepted_at, acceptance_due_at,"
                + " acceptance_overdue_recorded_at, resolution_type, resolved_at,"
                + " confirmation_due_at, onsite_eligible, closure_reason, closed_at, cancelled_at,"
                + " created_at, updated_at, row_version FROM sd_ticket WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_TicketEntity")
    TicketEntity lock(@Param("id") String id);

    /** 在锁取得后独立查询UTC决策时间。 */
    @Select("SELECT UTC_TIMESTAMP(6)")
    java.time.LocalDateTime decisionTime();

    /** 现场信息或终态变更前核对活动预约，P4接入后复用取消用例。 */
    @Select(
            "SELECT COUNT(*) FROM sd_onsite_booking WHERE ticket_id = #{ticket} AND status ="
                + " 'CONFIRMED'")
    long activeBookings(@Param("ticket") String ticket);
}
