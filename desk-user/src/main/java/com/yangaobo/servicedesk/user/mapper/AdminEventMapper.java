package com.yangaobo.servicedesk.user.mapper;

import com.yangaobo.servicedesk.user.model.entity.AdminEventEntity;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 不可变管理审计的业务边界。
 *
 * @author yangaobo
 */
public interface AdminEventMapper {
    /**
     * 与管理变更同事务追加不可变审计，不含口令。
     */
    int append(
            @Param("id") String id,
            @Param("actorType") String actorType,
            @Param("actor") String actor,
            @Param("user") String user,
            @Param("organization") String organization,
            @Param("type") String type,
            @Param("details") String details);

    /**
     * 按目标稳定分页读取不可变审计，查询前由服务验证管理员。
     */
    List<AdminEventEntity> list(
            @Param("user") String user,
            @Param("organization") String organization,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 复用相同目标条件统计总数。
     */
    long count(@Param("user") String user, @Param("organization") String organization);
}
