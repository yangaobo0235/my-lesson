package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 不可变工单事件的业务边界。
 *
 * @author yangaobo
 */
public interface TicketEventMapper extends BaseMapper<TicketEventEntity> {
    /**
     * 仅同事务新事件允许一次绑定正式回执；历史回执不能被改写。
     */
    int bindReceipt(@Param("id") String id, @Param("ticket") String ticket, @Param("receipt") String receipt);

    /**
     * 取得当前行锁并映射JSON字段。
     */
    TicketEventEntity lock(@Param("id") String id);

    /**
     * 读取工单可见时间线，列表与统计复用相同条件。
     *
     * @param ticketId 已验证读取权限的工单
     * @param customer 客户模式仅返回公开事件
     * @param limit    已校验的页大小或扫描上限
     * @param offset   已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<TicketEventEntity> listReadable(@Param("ticketId") String ticketId, @Param("customer") boolean customer, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取工单可见时间线的总数。
     *
     * @param ticketId 已验证读取权限的工单
     * @param customer 客户模式仅返回公开事件
     * @return 符合相同范围的总数
     */
    long countReadable(@Param("ticketId") String ticketId, @Param("customer") boolean customer);

    /**
     * 按明确集合或正序游标读取私有排障需要的工单事件
     *
     * @param ticketId      已验证技术人员范围的工单
     * @param eventIds      指定事件集合；空时使用正序游标
     * @param afterSequence 正序事件游标
     * @param limit         已校验的页大小或扫描上限
     * @return 按稳定顺序返回的有界结果
     */
    List<TicketEventEntity> readForDiagnosis(@Param("ticketId") String ticketId, @Param("eventIds") List<String> eventIds, @Param("afterSequence") long afterSequence, @Param("limit") int limit);
}
