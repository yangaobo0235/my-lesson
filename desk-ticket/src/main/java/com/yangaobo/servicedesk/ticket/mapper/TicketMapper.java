package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketQueryForm;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 工单的业务边界。
 *
 * @author yangaobo
 */
public interface TicketMapper extends BaseMapper<TicketEntity> {
    /**
     * 在调用者工单事务中比较并推进事件游标，不改变业务写版本。
     */
    int advanceEventSequence(@Param("id") String id, @Param("expected") long expected);

    /**
     * 取得当前行锁并映射JSON字段。
     */
    TicketEntity lock(@Param("id") String id);

    /**
     * 在锁取得后独立查询UTC决策时间。
     */
    LocalDateTime decisionTime();

    /**
     * 现场信息或终态变更前核对活动预约，P4接入后复用取消用例。
     */
    long activeBookings(@Param("ticket") String ticket);

    /**
     * 在当前工单授权范围内读取列表，列表与统计复用相同条件。
     *
     * @param actor     已验证角色与客户范围的当前身份
     * @param customer  是否客户本人模式
     * @param form      工单筛选
     * @param overdueAt 逾期筛选的数据库时刻；不筛选时为空
     * @param limit     已校验的页大小或扫描上限
     * @param offset    已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<TicketEntity> listTickets(@Param("actor") IdentityDTO actor, @Param("customer") boolean customer, @Param("form") TicketQueryForm form, @Param("overdueAt") LocalDateTime overdueAt, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 在当前工单授权范围内读取列表的总数。
     *
     * @param actor     已验证角色与客户范围的当前身份
     * @param customer  是否客户本人模式
     * @param form      工单筛选
     * @param overdueAt 逾期筛选的数据库时刻；不筛选时为空
     * @return 符合相同范围的总数
     */
    long countTickets(@Param("actor") IdentityDTO actor, @Param("customer") boolean customer, @Param("form") TicketQueryForm form, @Param("overdueAt") LocalDateTime overdueAt);

    /**
     * 有界扫描期限候选；调用者逐行加锁后重新核验
     *
     * @param now   本次扫描的数据库UTC时刻
     * @param limit 已校验的页大小或扫描上限
     * @return 按稳定顺序返回的有界结果
     */
    List<TicketEntity> scanDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
