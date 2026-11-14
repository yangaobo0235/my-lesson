package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * OnsiteBooking持久化及固定锁序读写。
 *
 * @author yangaobo
 */
public interface OnsiteBookingMapper extends BaseMapper<OnsiteBookingEntity> {
    /** 在工单和预约锁之后取得时段锁。 */
    @Select("SELECT id, ticket_id, slot_id, service_area_code, visit_address, client_request_id, request_hash, status, result_code, finish_result, confirmed_at, ended_at, created_at, updated_at FROM sd_onsite_booking WHERE id = #{id} FOR UPDATE")
    OnsiteBookingEntity lock(@Param("id") String id);
}
