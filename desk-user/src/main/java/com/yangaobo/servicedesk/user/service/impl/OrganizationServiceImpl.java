package com.yangaobo.servicedesk.user.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.user.mapper.AccountMapper;
import com.yangaobo.servicedesk.user.mapper.OrganizationMapper;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;
import com.yangaobo.servicedesk.user.model.form.organization.OrganizationCreateForm;
import com.yangaobo.servicedesk.user.model.form.organization.OrganizationUpdateForm;
import com.yangaobo.servicedesk.user.model.vo.organization.OrganizationVO;
import com.yangaobo.servicedesk.user.service.IIdentityAdministrationService;
import com.yangaobo.servicedesk.user.service.IOrganizationService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class OrganizationServiceImpl extends ServiceImpl<OrganizationMapper, OrganizationEntity>
        implements IOrganizationService {
    private final AccountMapper accountMapper;
    private final IIdentityAdministrationService administration;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<OrganizationVO> listOrganizations(IdentityDTO actor, IdentityQueryForm form) {
        administration.requireAdministrator(actor);
        String keyword = form.getKeyword() == null || form.getKeyword().isBlank()
                ? null : form.getKeyword().strip();
        long total = baseMapper.countOrganizations(form, keyword);
        var records = baseMapper.listOrganizations(form, keyword, form.getPageSize(), form.offset())
                .stream().map(this::view).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrganizationVO query(IdentityDTO actor, String id) {
        administration.requireAdministrator(actor);
        OrganizationEntity organization = baseMapper.selectById(id);
        if (organization == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return view(organization);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public OrganizationVO create(IdentityDTO actor, OrganizationCreateForm form) {
        administration.lockAdministrator(actor);
        OrganizationEntity organization = new OrganizationEntity();
        organization.setId(IdentifierGenerator.next());
        organization.setOrganizationCode(
                form.getOrganizationCode().strip().toLowerCase(Locale.ROOT));
        organization.setOrganizationName(form.getOrganizationName().strip());
        organization.setOrganizationType("CUSTOMER");
        organization.setStatus("ACTIVE");
        organization.setRowVersion(0L);
        organization.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        organization.setUpdatedAt(organization.getCreatedAt());
        baseMapper.insert(organization);
        OrganizationVO result = view(organization);
        administration.audit(
                actor.getOwnerId(),
                null,
                organization.getId(),
                "ORGANIZATION_CREATED",
                null,
                result,
                "管理员开通客户组织");
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public OrganizationVO updateOrganization(IdentityDTO actor, OrganizationUpdateForm form) {
        administration.lockAdministrator(actor);
        OrganizationEntity organization = baseMapper.lock(form.getId());
        if (organization == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (!organization.getRowVersion().equals(form.getRowVersion()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        OrganizationVO before = view(organization);
        organization.setOrganizationName(form.getOrganizationName().strip());
        organization.setStatus(form.getStatus());
        organization.setRowVersion(organization.getRowVersion() + 1);
        organization.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        baseMapper.updateById(organization);
        if (!before.getStatus().equals(organization.getStatus()))
            accountMapper.revokeOrganization(organization.getId());
        administration.ensureAdministrator();
        OrganizationVO after = view(organization);
        administration.audit(
                actor.getOwnerId(),
                null,
                organization.getId(),
                "ORGANIZATION_UPDATED",
                before,
                after,
                form.getReason());
        return after;
    }

    /**
     * 显式映射组织公开字段。
     */
    private OrganizationVO view(OrganizationEntity entity) {
        OrganizationVO result = new OrganizationVO();
        result.setId(entity.getId());
        result.setOrganizationCode(entity.getOrganizationCode());
        result.setOrganizationName(entity.getOrganizationName());
        result.setOrganizationType(entity.getOrganizationType());
        result.setStatus(entity.getStatus());
        result.setRowVersion(entity.getRowVersion());
        return result;
    }
}
