package com.yangaobo.servicedesk.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.error.OrganizationErrorCode;
import com.yangaobo.servicedesk.user.mapper.AccountMapper;
import com.yangaobo.servicedesk.user.mapper.OrganizationMapper;
import com.yangaobo.servicedesk.user.model.entity.AccountEntity;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;
import com.yangaobo.servicedesk.user.model.enums.RoleEnum;
import com.yangaobo.servicedesk.user.model.form.account.AccountCreateForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountRolesForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountScopesForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountStatusForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountUpdateForm;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;
import com.yangaobo.servicedesk.user.model.form.auth.PasswordResetForm;
import com.yangaobo.servicedesk.user.model.vo.account.AccountVO;
import com.yangaobo.servicedesk.user.service.IAccountService;
import com.yangaobo.servicedesk.user.service.IIdentityAdministrationService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 账号的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class AccountServiceImpl extends ServiceImpl<AccountMapper, AccountEntity>
        implements IAccountService {
    private final OrganizationMapper organizationMapper;
    private final IIdentityAdministrationService administration;
    private final PasswordEncoder passwordEncoder;

    /** {@inheritDoc} */
    @Override
    public PageVO<AccountVO> listAccounts(IdentityDTO actor, IdentityQueryForm form) {
        administration.requireAdministrator(actor);
        LambdaQueryWrapper<AccountEntity> query =
                new LambdaQueryWrapper<AccountEntity>()
                        .eq(
                                form.getOrganizationId() != null,
                                AccountEntity::getOrganizationId,
                                form.getOrganizationId())
                        .eq(form.getStatus() != null, AccountEntity::getStatus, form.getStatus());
        if (form.getKeyword() != null && !form.getKeyword().isBlank())
            query.likeRight(
                    AccountEntity::getUsername, form.getKeyword().strip().toLowerCase(Locale.ROOT));
        long total = baseMapper.selectCount(query);
        query.orderByAsc(AccountEntity::getId)
                .last(
                        "LIMIT "
                                + form.getPageSize()
                                + " OFFSET "
                                + ((long) (form.getPageNum() - 1) * form.getPageSize()));
        PageVO<AccountVO> result = new PageVO<>();
        result.setRecords(baseMapper.selectList(query).stream().map(this::view).toList());
        result.setTotal(total);
        result.setPageNum(form.getPageNum());
        result.setPageSize(form.getPageSize());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AccountVO query(IdentityDTO actor, String id) {
        administration.requireAdministrator(actor);
        AccountEntity account = baseMapper.selectById(id);
        if (account == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return view(account);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountVO create(IdentityDTO actor, AccountCreateForm form) {
        administration.lockAdministrator(actor);
        OrganizationEntity organization = activeOrganization(form.getOrganizationId());
        administration.validateRoles(organization.getOrganizationType(), form.getRoles());
        administration.validatePassword(form.getInitialPassword());
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID().toString().replace("-", ""));
        account.setOrganizationId(organization.getId());
        account.setUsername(form.getUsername().strip().toLowerCase(Locale.ROOT));
        account.setDisplayName(form.getDisplayName().strip());
        account.setPasswordHash(passwordEncoder.encode(form.getInitialPassword()));
        account.setStatus("ACTIVE");
        account.setMustChangePassword(true);
        account.setCreatedBy(actor.getOwnerId());
        account.setAuthVersion(0L);
        account.setRowVersion(0L);
        account.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        account.setUpdatedAt(account.getCreatedAt());
        baseMapper.insert(account);
        for (String role : new TreeSet<>(form.getRoles()))
            baseMapper.grantRole(account.getId(), role, actor.getOwnerId());
        AccountVO result = view(account);
        administration.audit(
                actor.getOwnerId(),
                account.getId(),
                null,
                "ACCOUNT_CREATED",
                null,
                result,
                "管理员开通账号");
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountVO updateAccount(IdentityDTO actor, AccountUpdateForm form) {
        AccountEntity account = locked(actor, form.getId(), form.getRowVersion());
        AccountVO before = view(account);
        OrganizationEntity organization = activeOrganization(form.getOrganizationId());
        administration.validateRoles(organization.getOrganizationType(), before.getRoles());
        account.setOrganizationId(organization.getId());
        account.setDisplayName(form.getDisplayName().strip());
        return finish(actor, account, before, "ACCOUNT_UPDATED", form.getReason());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountVO setRoles(IdentityDTO actor, AccountRolesForm form) {
        AccountEntity account = locked(actor, form.getId(), form.getRowVersion());
        AccountVO before = view(account);
        OrganizationEntity organization =
                organizationMapper.selectById(account.getOrganizationId());
        administration.validateRoles(organization.getOrganizationType(), form.getRoles());
        baseMapper.clearRoles(account.getId());
        for (String role : new TreeSet<>(form.getRoles()))
            baseMapper.grantRole(account.getId(), role, actor.getOwnerId());
        if (!form.getRoles().contains(RoleEnum.SERVICE_AGENT.getValue())
                && !form.getRoles().contains(RoleEnum.TECHNICIAN.getValue()))
            baseMapper.clearScopes(account.getId());
        return finish(actor, account, before, "ROLES_CHANGED", form.getReason());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountVO setScopes(IdentityDTO actor, AccountScopesForm form) {
        AccountEntity account = locked(actor, form.getId(), form.getRowVersion());
        AccountVO before = view(account);
        if (!before.getRoles().contains(RoleEnum.SERVICE_AGENT.getValue())
                && !before.getRoles().contains(RoleEnum.TECHNICIAN.getValue()))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        for (String id : new TreeSet<>(form.getCustomerIds())) {
            OrganizationEntity organization = activeOrganization(id);
            if (!"CUSTOMER".equals(organization.getOrganizationType()))
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        baseMapper.clearScopes(account.getId());
        for (String id : new TreeSet<>(form.getCustomerIds()))
            baseMapper.grantScope(account.getId(), id, actor.getOwnerId());
        return finish(actor, account, before, "CUSTOMER_SCOPE_CHANGED", form.getReason());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountVO setStatus(IdentityDTO actor, AccountStatusForm form, boolean enabled) {
        AccountEntity account = locked(actor, form.getId(), form.getRowVersion());
        AccountVO before = view(account);
        account.setStatus(enabled ? "ACTIVE" : "DISABLED");
        return finish(
                actor,
                account,
                before,
                enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED",
                form.getReason());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void resetPassword(IdentityDTO actor, PasswordResetForm form) {
        administration.validatePassword(form.getNewPassword());
        AccountEntity account = locked(actor, form.getId(), form.getRowVersion());
        AccountVO before = view(account);
        account.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        account.setMustChangePassword(true);
        finish(actor, account, before, "PASSWORD_RESET", form.getReason());
    }

    /** 统一按服务商组织、目标账号锁序校验版本。 */
    private AccountEntity locked(IdentityDTO actor, String id, long version) {
        administration.lockAdministrator(actor);
        AccountEntity account = baseMapper.lock(id);
        if (account == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (account.getRowVersion() != version)
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return account;
    }

    /** 版本、身份撤销及安全审计全部随当前事务提交。 */
    private AccountVO finish(
            IdentityDTO actor,
            AccountEntity account,
            AccountVO before,
            String event,
            String reason) {
        account.setAuthVersion(account.getAuthVersion() + 1);
        account.setRowVersion(account.getRowVersion() + 1);
        account.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        baseMapper.updateById(account);
        administration.ensureAdministrator();
        AccountVO after = view(account);
        administration.audit(
                actor.getOwnerId(), account.getId(), null, event, before, after, reason);
        return after;
    }

    /** 新关联组织必须有效。 */
    private OrganizationEntity activeOrganization(String id) {
        OrganizationEntity organization = organizationMapper.selectById(id);
        if (organization == null || !"ACTIVE".equals(organization.getStatus()))
            throw new BusinessException(OrganizationErrorCode.ORGANIZATION_UNAVAILABLE);
        return organization;
    }

    /** 显式投影可公开字段，口令永不进入VO或审计。 */
    private AccountVO view(AccountEntity account) {
        AccountVO result = new AccountVO();
        result.setId(account.getId());
        result.setOrganizationId(account.getOrganizationId());
        result.setUsername(account.getUsername());
        result.setDisplayName(account.getDisplayName());
        result.setStatus(account.getStatus());
        result.setMustChangePassword(account.getMustChangePassword());
        result.setRowVersion(account.getRowVersion());
        result.setRoles(baseMapper.roles(account.getId()));
        result.setCustomerIds(baseMapper.scopes(account.getId()));
        return result;
    }
}
