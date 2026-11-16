package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEntitlementEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

/**
 * 客户服务资格的业务边界。
 *
 * @author yangaobo
 */
public interface ServiceEntitlementMapper extends BaseMapper<ServiceEntitlementEntity> {
    /**
     * 按稳定标识锁定当前事实，JSON使用实体明确映射。
     */
    ServiceEntitlementEntity lock(@Param("id") String id);

    /**
     * 服务项目锁之后再锁定同客户资格。
     */
    ServiceEntitlementEntity lockCustomer(
            @Param("customer") String customer, @Param("service") String service);

    /**
     * 为未分类受理选择稳定的有效候选，取得锁后仍须重核。
     */
    String admissionCandidate(@Param("customer") String customer);

    /**
     * 独立查询锁取得之后的UTC决策时刻。
     */
    LocalDateTime decisionTime();

    /**
     * 在已授权客户集合内读取资源，列表与统计复用相同条件。
     *
     * @param customerIds 服务端已授权客户集合；空集合不返回数据
     * @param status      可选状态
     * @param limit       已校验的页大小或扫描上限
     * @param offset      已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<ServiceEntitlementEntity> listEntitlements(@Param("customerIds") Set<String> customerIds, @Param("status") String status, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 在已授权客户集合内读取资源的总数。
     *
     * @param customerIds 服务端已授权客户集合；空集合不返回数据
     * @param status      可选状态
     * @return 符合相同范围的总数
     */
    long countEntitlements(@Param("customerIds") Set<String> customerIds, @Param("status") String status);

    /**
     * 读取有效服务项目对应的当前客户资格，列表与统计复用相同条件。
     *
     * @param customer 已授权目标客户
     * @param today    数据库决策时刻对应的北京时间日期
     * @param limit    已校验的页大小或扫描上限
     * @param offset   已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<ServiceEntitlementEntity> listAvailable(@Param("customer") String customer, @Param("today") LocalDate today, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取有效服务项目对应的当前客户资格的总数。
     *
     * @param customer 已授权目标客户
     * @param today    数据库决策时刻对应的北京时间日期
     * @return 符合相同范围的总数
     */
    long countAvailable(@Param("customer") String customer, @Param("today") LocalDate today);
}
