package com.yangaobo.servicedesk.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 组织的业务边界。
 *
 * @author yangaobo
 */
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {
    /** 锁定唯一服务商，所有身份管理写入共用此串行化边界。 */
    @Select(
            "SELECT id, organization_code, organization_name, organization_type, status,"
                + " created_at, updated_at, row_version FROM sys_organization WHERE"
                + " organization_type = 'SERVICE_PROVIDER' FOR UPDATE")
    OrganizationEntity lockProvider();

    /** 在锁内取得组织当前事实。 */
    @Select(
            "SELECT id, organization_code, organization_name, organization_type, status,"
                + " created_at, updated_at, row_version FROM sys_organization WHERE id = #{id} FOR"
                + " UPDATE")
    OrganizationEntity lock(@Param("id") String id);
}
