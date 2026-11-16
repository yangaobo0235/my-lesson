package com.yangaobo.servicedesk.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.user.model.entity.AccountEntity;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

/**
 * 账号的业务边界。
 *
 * @author yangaobo
 */
public interface AccountMapper extends BaseMapper<AccountEntity> {
    /**
     * 锁定受影响账号；调用前先锁服务商组织。
     */
    AccountEntity lock(@Param("id") String id);

    /**
     * 读取当前角色，不信任会话内角色副本。
     */
    Set<String> roles(@Param("id") String id);

    /**
     * 读取当前客户范围。
     */
    Set<String> scopes(@Param("id") String id);

    /**
     * 在同一事务内重建角色前移除旧关联。
     */
    int clearRoles(@Param("id") String id);

    /**
     * 写入明确授予的角色。
     */
    int grantRole(@Param("id") String id, @Param("role") String role, @Param("actor") String actor);

    /**
     * 清理被撤销的客户范围。
     */
    int clearScopes(@Param("id") String id);

    /**
     * 写入范围授权。
     */
    int grantScope(
            @Param("id") String id,
            @Param("customer") String customer,
            @Param("actor") String actor);

    /**
     * 在服务商组织锁内核对有效管理员，不将COUNT作为独立并发保证。
     */
    long activeAdministrators();

    /**
     * 组织停用使该组织所有旧会话失效，重新启用不复活旧版本。
     */
    int revokeOrganization(@Param("id") String id);

    /**
     * 将不可登录的主键引用解析为只在user内部使用的会话定位。
     */
    String sessionId(@Param("id") String id);

    /**
     * 取得不可登录会话引用。
     */
    String sessionRef(@Param("id") String id);

    /**
     * 按管理员已验证范围读取Account，列表与统计复用相同条件。
     *
     * @param form    管理查询条件
     * @param keyword 服务端规范化的前缀；无筛选时为空
     * @param limit   已校验的页大小或扫描上限
     * @param offset  已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<AccountEntity> listAccounts(@Param("form") IdentityQueryForm form, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 按管理员已验证范围读取Account的总数。
     *
     * @param form    管理查询条件
     * @param keyword 服务端规范化的前缀；无筛选时为空
     * @return 符合相同范围的总数
     */
    long countAccounts(@Param("form") IdentityQueryForm form, @Param("keyword") String keyword);
}
