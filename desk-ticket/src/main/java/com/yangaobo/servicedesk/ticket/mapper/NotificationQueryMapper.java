package com.yangaobo.servicedesk.ticket.mapper;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.NotificationVO;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 有权通知分页查询的业务边界。
 *
 * @author yangaobo
 */
public interface NotificationQueryMapper {
    /**
     * 当前有权通知的数据库分页，不返回撤权资源的标题。
     */
    List<NotificationVO> list(
            @Param("actor") IdentityDTO actor,
            @Param("customer") boolean customer,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 与列表相同授权条件的总数。
     */
    long count(@Param("actor") IdentityDTO actor, @Param("customer") boolean customer);
}
