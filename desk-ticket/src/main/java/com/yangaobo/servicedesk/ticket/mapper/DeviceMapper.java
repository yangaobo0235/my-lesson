package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.DeviceEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 客户可选资产的业务边界。
 *
 * @author yangaobo
 */
public interface DeviceMapper extends BaseMapper<DeviceEntity> {
    /** 按稳定标识锁定当前事实，JSON使用实体明确映射。 */
    @Select(
            "SELECT id, customer_id, product_model_id, asset_code, serial_number, location,"
                + " service_area_code, status, created_by, created_at, updated_at, row_version FROM"
                + " it_device WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_DeviceEntity")
    DeviceEntity lock(@Param("id") String id);
}
