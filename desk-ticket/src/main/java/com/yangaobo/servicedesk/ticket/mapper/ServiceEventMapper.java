package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEventEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 项目与资格变更审计的业务边界。
 *
 * @author yangaobo
 */
public interface ServiceEventMapper extends BaseMapper<ServiceEventEntity> {
    /** 按稳定标识锁定当前事实，JSON使用实体明确映射。 */
    @Select(
            "SELECT id, service_item_id, entitlement_id, actor_id, event_type, client_action_id,"
                + " request_hash, details, created_at FROM it_service_event WHERE id = #{id} FOR"
                + " UPDATE")
    @ResultMap("mybatis-plus_ServiceEventEntity")
    ServiceEventEntity lock(@Param("id") String id);
}
