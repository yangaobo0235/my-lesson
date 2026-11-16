package com.yangaobo.servicedesk.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;
import com.yangaobo.servicedesk.user.model.form.organization.OrganizationCreateForm;
import com.yangaobo.servicedesk.user.model.form.organization.OrganizationUpdateForm;
import com.yangaobo.servicedesk.user.model.vo.organization.OrganizationVO;

/**
 * 组织的业务边界。
 *
 * @author yangaobo
 */
public interface IOrganizationService extends IService<OrganizationEntity> {
    /**
     * 分页查询授权管理组织。
     */
    PageVO<OrganizationVO> listOrganizations(IdentityDTO actor, IdentityQueryForm form);

    /**
     * 读取组织详情。
     */
    OrganizationVO query(IdentityDTO actor, String id);

    /**
     * 创建客户组织，不开放第二个服务商入口。
     */
    OrganizationVO create(IdentityDTO actor, OrganizationCreateForm form);

    /**
     * 修改组织，停用与授权版本更新同事务。
     */
    OrganizationVO updateOrganization(IdentityDTO actor, OrganizationUpdateForm form);
}
