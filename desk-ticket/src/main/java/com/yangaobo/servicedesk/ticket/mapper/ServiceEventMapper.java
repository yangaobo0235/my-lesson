package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEventEntity;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 项目与资格变更审计的业务边界。
 *
 * @author yangaobo
 */
public interface ServiceEventMapper extends BaseMapper<ServiceEventEntity> {
    /**
     * 按稳定标识锁定当前事实，JSON使用实体明确映射。
     */
    ServiceEventEntity lock(@Param("id") String id);

    /**
     * 读取已授权目标的不可变变更事件，列表与统计复用相同条件。
     *
     * @param id          已授权项目或资格标识
     * @param entitlement 是否按资格而非项目读取
     * @param limit       已校验的页大小或扫描上限
     * @param offset      已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<ServiceEventEntity> listEvents(@Param("id") String id, @Param("entitlement") boolean entitlement, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取已授权目标的不可变变更事件的总数。
     *
     * @param id          已授权项目或资格标识
     * @param entitlement 是否按资格而非项目读取
     * @return 符合相同范围的总数
     */
    long countEvents(@Param("id") String id, @Param("entitlement") boolean entitlement);
}
