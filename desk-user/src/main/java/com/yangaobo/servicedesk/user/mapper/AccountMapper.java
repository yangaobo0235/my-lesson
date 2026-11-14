package com.yangaobo.servicedesk.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.user.model.entity.AccountEntity;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Set;

/**
 * 账号的业务边界。
 *
 * @author yangaobo
 */
public interface AccountMapper extends BaseMapper<AccountEntity> {
    /** 锁定受影响账号；调用前先锁服务商组织。 */
    @Select(
            "SELECT id, organization_id, username, password_hash, display_name, status,"
                + " must_change_password, password_changed_at, last_login_at, auth_version,"
                + " created_by, created_at, updated_at, row_version FROM sys_user WHERE id = #{id}"
                + " FOR UPDATE")
    AccountEntity lock(@Param("id") String id);

    /** 读取当前角色，不信任会话内角色副本。 */
    @Select("SELECT role_code FROM sys_user_role WHERE user_id = #{id} ORDER BY role_code")
    Set<String> roles(@Param("id") String id);

    /** 读取当前客户范围。 */
    @Select(
            "SELECT customer_id FROM sys_user_customer_scope WHERE user_id = #{id} ORDER BY"
                + " customer_id")
    Set<String> scopes(@Param("id") String id);

    /** 在同一事务内重建角色前移除旧关联。 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{id}")
    int clearRoles(@Param("id") String id);

    /** 写入明确授予的角色。 */
    @Insert(
            "INSERT INTO sys_user_role(user_id, role_code, granted_by) VALUES(#{id}, #{role},"
                + " #{actor})")
    int grantRole(@Param("id") String id, @Param("role") String role, @Param("actor") String actor);

    /** 清理被撤销的客户范围。 */
    @Delete("DELETE FROM sys_user_customer_scope WHERE user_id = #{id}")
    int clearScopes(@Param("id") String id);

    /** 写入范围授权。 */
    @Insert(
            "INSERT INTO sys_user_customer_scope(user_id, customer_id, granted_by) VALUES(#{id},"
                + " #{customer}, #{actor})")
    int grantScope(
            @Param("id") String id,
            @Param("customer") String customer,
            @Param("actor") String actor);

    /** 在服务商组织锁内核对有效管理员，不将COUNT作为独立并发保证。 */
    @Select(
            """
SELECT COUNT(*) FROM sys_user u
JOIN sys_organization o ON o.id = u.organization_id
JOIN sys_user_role r ON r.user_id = u.id AND r.role_code = 'SYSTEM_ADMIN'
WHERE u.status = 'ACTIVE' AND o.status = 'ACTIVE' AND o.organization_type = 'SERVICE_PROVIDER'
""")
    long activeAdministrators();

    /** 组织停用使该组织所有旧会话失效，重新启用不复活旧版本。 */
    @Update(
            "UPDATE sys_user SET auth_version = auth_version + 1, row_version = row_version + 1,"
                + " updated_at = UTC_TIMESTAMP(6) WHERE organization_id = #{id}")
    int revokeOrganization(@Param("id") String id);

    /** 将不可登录的主键引用解析为只在user内部使用的会话定位。 */
    @Select("SELECT SESSION_ID FROM SPRING_SESSION WHERE PRIMARY_ID = #{id}")
    String sessionId(@Param("id") String id);

    /** 取得不可登录会话引用。 */
    @Select("SELECT PRIMARY_ID FROM SPRING_SESSION WHERE SESSION_ID = #{id}")
    String sessionRef(@Param("id") String id);
}
