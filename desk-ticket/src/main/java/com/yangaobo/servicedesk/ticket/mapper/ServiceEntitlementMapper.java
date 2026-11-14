package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEntitlementEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 客户服务资格的业务边界。
 *
 * @author yangaobo
 */
public interface ServiceEntitlementMapper extends BaseMapper<ServiceEntitlementEntity> {
    /** 按稳定标识锁定当前事实，JSON使用实体明确映射。 */
    @Select(
            "SELECT id, customer_id, service_item_id, starts_on, ends_on, status, granted_by,"
                + " updated_by, change_reason, created_at, updated_at, row_version FROM"
                + " it_service_entitlement WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_ServiceEntitlementEntity")
    ServiceEntitlementEntity lock(@Param("id") String id);

    /** 服务项目锁之后再锁定同客户资格。 */
    @Select(
            "SELECT id, customer_id, service_item_id, starts_on, ends_on, status, granted_by,"
                + " updated_by, change_reason, created_at, updated_at, row_version FROM"
                + " it_service_entitlement WHERE customer_id = #{customer} AND service_item_id ="
                + " #{service} FOR UPDATE")
    ServiceEntitlementEntity lockCustomer(
            @Param("customer") String customer, @Param("service") String service);

    /** 为未分类受理选择稳定的有效候选，取得锁后仍须重核。 */
    @Select(
            """
            SELECT e.service_item_id FROM it_service_entitlement e
            JOIN it_service_item s ON s.id = e.service_item_id AND s.status = 'ACTIVE'
            WHERE e.customer_id = #{customer} AND e.status = 'ACTIVE'
                AND DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR) BETWEEN e.starts_on AND e.ends_on
            ORDER BY e.service_item_id LIMIT 1
            """)
    String admissionCandidate(@Param("customer") String customer);

    /** 独立查询锁取得之后的UTC决策时刻。 */
    @Select("SELECT UTC_TIMESTAMP(6)")
    java.time.LocalDateTime decisionTime();
}
