package com.yangaobo.servicedesk.user.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;

import java.util.Set;

/**
 * 身份管理授权与审计的业务边界。
 *
 * @author yangaobo
 */
public interface IIdentityAdministrationService {
    /**
     * 查询时核对当前管理员与强制改密状态。
     */
    void requireAdministrator(IdentityDTO actor);

    /**
     * 在现有事务中先锁唯一服务商并重核当前管理员。
     */
    void lockAdministrator(IdentityDTO actor);

    /**
     * 管理变更结束时核对仍有有效管理员，失败回滚全部变更。
     */
    void ensureAdministrator();

    /**
     * 与业务写入同事务记录安全审计前后值。
     */
    void audit(
            String actor,
            String user,
            String organization,
            String type,
            Object before,
            Object after,
            String reason);

    /**
     * 校验创建或变更后的组织角色组合。
     */
    void validateRoles(String organizationType, Set<String> roles);

    /**
     * 校验新密码的字节预算、强度和非空约束。
     */
    void validatePassword(String password);
}
