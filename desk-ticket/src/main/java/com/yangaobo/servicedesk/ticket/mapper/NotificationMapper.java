package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.NotificationEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 站内通知的业务边界。
 *
 * @author yangaobo
 */
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
    /**
     * 按稳定标识锁定当前事实，JSON使用实体明确映射。
     */
    NotificationEntity lock(@Param("id") String id);
}
