package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * OnsiteBooking持久化及固定锁序读写。
 *
 * @author yangaobo
 */
public interface OnsiteBookingMapper extends BaseMapper<OnsiteBookingEntity> {
    /**
     * 在工单和预约锁之后取得时段锁。
     */
    OnsiteBookingEntity lock(@Param("id") String id);

    /**
     * 读取同一工单的预约历史，列表与统计复用相同条件。
     *
     * @param ticketId 已验证读取权限的工单
     * @param limit    已校验的页大小或扫描上限
     * @param offset   已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<OnsiteBookingEntity> listHistory(@Param("ticketId") String ticketId, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取同一工单的预约历史的总数。
     *
     * @param ticketId 已验证读取权限的工单
     * @return 符合相同范围的总数
     */
    long countHistory(@Param("ticketId") String ticketId);
}
