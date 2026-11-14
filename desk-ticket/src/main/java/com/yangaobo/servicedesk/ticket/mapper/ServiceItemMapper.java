package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * IT服务项目的业务边界。
 *
 * @author yangaobo
 */
public interface ServiceItemMapper extends BaseMapper<ServiceItemEntity> {
    /** 按稳定标识锁定当前事实，JSON使用实体明确映射。 */
    @Select(
            "SELECT id, service_code, category, service_name, support_scope, onsite_supported,"
                + " status, created_by, updated_by, change_reason, created_at, updated_at,"
                + " row_version FROM it_service_item WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_ServiceItemEntity")
    ServiceItemEntity lock(@Param("id") String id);
}
