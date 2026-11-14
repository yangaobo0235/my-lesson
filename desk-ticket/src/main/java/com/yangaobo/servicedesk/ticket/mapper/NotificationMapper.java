package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.NotificationEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 站内通知的业务边界。
 *
 * @author yangaobo
 */
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
    /** 按稳定标识锁定当前事实，JSON使用实体明确映射。 */
    @Select(
            "SELECT id, ticket_id, event_id, recipient_id, read_at, created_at FROM sd_notification"
                + " WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_NotificationEntity")
    NotificationEntity lock(@Param("id") String id);
}
