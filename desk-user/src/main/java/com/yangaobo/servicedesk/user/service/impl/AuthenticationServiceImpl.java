package com.yangaobo.servicedesk.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.component.LoginAttemptLimiter;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;
import com.yangaobo.servicedesk.user.mapper.AccountMapper;
import com.yangaobo.servicedesk.user.mapper.OrganizationMapper;
import com.yangaobo.servicedesk.user.model.entity.AccountEntity;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;
import com.yangaobo.servicedesk.user.model.form.auth.LoginForm;
import com.yangaobo.servicedesk.user.model.form.auth.PasswordForm;
import com.yangaobo.servicedesk.user.security.PasswordPolicy;
import com.yangaobo.servicedesk.user.service.IAuthenticationService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * 登录与当前会话的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class AuthenticationServiceImpl extends com.baomidou.mybatisplus.spring.service.impl.ServiceImpl<AccountMapper, AccountEntity> implements IAuthenticationService {
    private final OrganizationMapper organizationMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    // 对不存在的账号也执行固定成本哈希，避免显著时序泄漏。
    private final String dummyHash =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .encode("unusable-dummy-password-29381");

    /** {@inheritDoc} */
    @Override
    public IdentityDTO verify(String ownerId, long authVersion, String sessionRef) {
        AccountEntity account = baseMapper.selectById(ownerId);
        if (account == null
                || !"ACTIVE".equals(account.getStatus())
                || account.getAuthVersion() != authVersion) {
            throw new BusinessException(AuthenticationErrorCode.SESSION_EXPIRED);
        }
        OrganizationEntity organization =
                organizationMapper.selectById(account.getOrganizationId());
        if (organization == null || !"ACTIVE".equals(organization.getStatus()))
            throw new BusinessException(AuthenticationErrorCode.SESSION_EXPIRED);
        IdentityDTO identity = new IdentityDTO();
        identity.setOwnerId(account.getId());
        identity.setOrganizationId(organization.getId());
        identity.setOrganizationType(organization.getOrganizationType());
        identity.setDisplayName(account.getDisplayName());
        identity.setRoles(baseMapper.roles(account.getId()));
        identity.setCustomerIds(baseMapper.scopes(account.getId()));
        identity.setAuthVersion(account.getAuthVersion());
        identity.setSessionRef(sessionRef);
        identity.setMustChangePassword(account.getMustChangePassword());
        return identity;
    }

    /** {@inheritDoc} */
    @Override
    public IdentityDTO login(LoginForm form, String remoteAddress) {
        String username = form.getUsername().strip().toLowerCase(Locale.ROOT);
        loginAttemptLimiter.acquire(username, remoteAddress);
        AccountEntity account =
                baseMapper.selectOne(
                        new LambdaQueryWrapper<AccountEntity>()
                                .eq(AccountEntity::getUsername, username));
        String hash = account == null ? "{bcrypt}" + dummyHash : account.getPasswordHash();
        boolean matches = passwordEncoder.matches(form.getPassword(), hash);
        if (!matches || account == null || !"ACTIVE".equals(account.getStatus()))
            throw new BusinessException(AuthenticationErrorCode.INVALID_CREDENTIALS);
        OrganizationEntity organization =
                organizationMapper.selectById(account.getOrganizationId());
        if (organization == null || !"ACTIVE".equals(organization.getStatus()))
            throw new BusinessException(AuthenticationErrorCode.INVALID_CREDENTIALS);
        IdentityDTO identity = verify(account.getId(), account.getAuthVersion(), null);
        baseMapper.update(
                null,
                new LambdaUpdateWrapper<AccountEntity>()
                        .eq(AccountEntity::getId, account.getId())
                        .eq(AccountEntity::getAuthVersion, account.getAuthVersion())
                        .set(AccountEntity::getLastLoginAt, LocalDateTime.now(ZoneOffset.UTC)));
        return identity;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void changePassword(IdentityDTO actor, PasswordForm form) {
        PasswordPolicy.validate(form.getNewPassword());
        organizationMapper.lockProvider();
        AccountEntity account = baseMapper.lock(actor.getOwnerId());
        verify(actor.getOwnerId(), actor.getAuthVersion(), actor.getSessionRef());
        if (!passwordEncoder.matches(form.getCurrentPassword(), account.getPasswordHash()))
            throw new BusinessException(AuthenticationErrorCode.INVALID_CREDENTIALS);
        if (passwordEncoder.matches(form.getNewPassword(), account.getPasswordHash()))
            throw new BusinessException(AuthenticationErrorCode.INVALID_PASSWORD);
        account.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        account.setMustChangePassword(false);
        account.setAuthVersion(account.getAuthVersion() + 1);
        account.setRowVersion(account.getRowVersion() + 1);
        account.setPasswordChangedAt(LocalDateTime.now(ZoneOffset.UTC));
        account.setUpdatedAt(account.getPasswordChangedAt());
        baseMapper.updateById(account);
    }
}
