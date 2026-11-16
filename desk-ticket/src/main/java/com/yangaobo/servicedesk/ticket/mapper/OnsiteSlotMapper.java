package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * OnsiteSlot持久化及固定锁序读写。
 *
 * @author yangaobo
 */
public interface OnsiteSlotMapper extends BaseMapper<OnsiteSlotEntity> {
    /**
     * 在工单和预约锁之后取得时段锁。
     */
    OnsiteSlotEntity lock(@Param("id") String id);

    /**
     * 读取管理员时段目录，列表与统计复用相同条件。
     *
     * @param limit  已校验的页大小或扫描上限
     * @param offset 已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<OnsiteSlotEntity> listSlots(@Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取管理员时段目录的总数。
     *
     * @return 符合相同范围的总数
     */
    long countSlots();

    /**
     * 读取已授权工单在当前窗口可预约的时段，列表与统计复用相同条件。
     *
     * @param area   已授权工单的服务区域
     * @param now    数据库UTC决策时刻
     * @param limit  已校验的页大小或扫描上限
     * @param offset 已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<OnsiteSlotEntity> listBookable(@Param("area") String area, @Param("now") LocalDateTime now, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取已授权工单在当前窗口可预约的时段的总数。
     *
     * @param area 已授权工单的服务区域
     * @param now  数据库UTC决策时刻
     * @return 符合相同范围的总数
     */
    long countBookable(@Param("area") String area, @Param("now") LocalDateTime now);
}
