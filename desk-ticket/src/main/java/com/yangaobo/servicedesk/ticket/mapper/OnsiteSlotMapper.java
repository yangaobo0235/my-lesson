package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * OnsiteSlot持久化及固定锁序读写。
 *
 * @author yangaobo
 */
public interface OnsiteSlotMapper extends BaseMapper<OnsiteSlotEntity> {
    /** 在工单和预约锁之后取得时段锁。 */
    @Select("SELECT id, service_area_code, starts_at, ends_at, booking_opens_at, booking_closes_at, capacity, reserved_count, status, created_by, published_by, closed_by, published_at, closed_at, created_at, updated_at, row_version FROM sd_onsite_slot WHERE id = #{id} FOR UPDATE")
    OnsiteSlotEntity lock(@Param("id") String id);
}
