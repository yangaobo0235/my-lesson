package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.DeviceEntity;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

/**
 * 客户可选资产的业务边界。
 *
 * @author yangaobo
 */
public interface DeviceMapper extends BaseMapper<DeviceEntity> {
    /**
     * 按稳定标识锁定当前事实，JSON使用实体明确映射。
     */
    DeviceEntity lock(@Param("id") String id);

    /**
     * 在已授权客户集合内读取资源，列表与统计复用相同条件。
     *
     * @param customerIds 服务端已授权客户集合；空集合不返回数据
     * @param status      可选状态
     * @param limit       已校验的页大小或扫描上限
     * @param offset      已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<DeviceEntity> listDevices(@Param("customerIds") Set<String> customerIds, @Param("status") String status, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 在已授权客户集合内读取资源的总数。
     *
     * @param customerIds 服务端已授权客户集合；空集合不返回数据
     * @param status      可选状态
     * @return 符合相同范围的总数
     */
    long countDevices(@Param("customerIds") Set<String> customerIds, @Param("status") String status);
}
