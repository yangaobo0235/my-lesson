package com.yangaobo.servicedesk.user.service.impl;

import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.user.config.BootstrapProperties;
import com.yangaobo.servicedesk.user.mapper.AccountMapper;
import com.yangaobo.servicedesk.user.mapper.OrganizationMapper;
import com.yangaobo.servicedesk.user.model.entity.AccountEntity;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;
import com.yangaobo.servicedesk.user.model.enums.RoleEnum;
import com.yangaobo.servicedesk.user.security.PasswordPolicy;
import com.yangaobo.servicedesk.user.service.IBootstrapService;
import com.yangaobo.servicedesk.user.service.IIdentityAdministrationService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受控首名管理员初始化的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapServiceImpl implements IBootstrapService {
    private final OrganizationMapper organizationMapper;
    private final AccountMapper accountMapper;
    private final IIdentityAdministrationService administration;
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc} 唯一服务商约束处理并发首次初始化，失败整笔回滚。
     */
    @Override
    @Transactional
    public boolean initialize(BootstrapProperties properties) {
        if (organizationMapper.lockProvider() != null) return false;
        PasswordPolicy.validate(properties.getPassword());
        String username =
                properties.getUsername() == null
                        ? ""
                        : properties.getUsername().strip().toLowerCase(Locale.ROOT);
        String code =
                properties.getOrganizationCode() == null
                        ? ""
                        : properties.getOrganizationCode().strip().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,63}")
                || !code.matches("[a-z0-9][a-z0-9_-]{2,63}")
                || properties.getOrganizationName() == null
                || properties.getOrganizationName().isBlank()
                || properties.getOrganizationName().length() > 100)
            throw new IllegalArgumentException("初始化名称或账号不满足合同");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        OrganizationEntity organization = new OrganizationEntity();
        organization.setId(IdentifierGenerator.next());
        organization.setOrganizationCode(code);
        organization.setOrganizationName(properties.getOrganizationName().strip());
        organization.setOrganizationType("SERVICE_PROVIDER");
        organization.setStatus("ACTIVE");
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);
        organization.setRowVersion(0L);
        organizationMapper.insert(organization);
        AccountEntity account = new AccountEntity();
        account.setId(IdentifierGenerator.next());
        account.setOrganizationId(organization.getId());
        account.setUsername(username);
        account.setDisplayName("系统管理员");
        account.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
        account.setMustChangePassword(true);
        account.setStatus("ACTIVE");
        account.setAuthVersion(0L);
        account.setRowVersion(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountMapper.insert(account);
        accountMapper.grantRole(account.getId(), RoleEnum.SYSTEM_ADMIN.getValue(), null);
        administration.audit(
                null,
                null,
                organization.getId(),
                "ORGANIZATION_CREATED",
                null,
                organization.getOrganizationName(),
                "受控初始化服务商");
        administration.audit(
                null,
                account.getId(),
                null,
                "ACCOUNT_CREATED",
                null,
                account.getUsername(),
                "受控初始化管理员");
        return true;
    }
}
