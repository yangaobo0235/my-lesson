package com.yangaobo.servicedesk.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 组织的业务边界。
 *
 * @author yangaobo
 */
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {
    /**
     * 锁定唯一服务商，所有身份管理写入共用此串行化边界。
     */
    OrganizationEntity lockProvider();

    /**
     * 在锁内取得组织当前事实。
     */
    OrganizationEntity lock(@Param("id") String id);

    /**
     * 按管理员已验证范围读取Organization，列表与统计复用相同条件。
     *
     * @param form    管理查询条件
     * @param keyword 服务端规范化的前缀；无筛选时为空
     * @param limit   已校验的页大小或扫描上限
     * @param offset  已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<OrganizationEntity> listOrganizations(@Param("form") IdentityQueryForm form, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 按管理员已验证范围读取Organization的总数。
     *
     * @param form    管理查询条件
     * @param keyword 服务端规范化的前缀；无筛选时为空
     * @return 符合相同范围的总数
     */
    long countOrganizations(@Param("form") IdentityQueryForm form, @Param("keyword") String keyword);
}
