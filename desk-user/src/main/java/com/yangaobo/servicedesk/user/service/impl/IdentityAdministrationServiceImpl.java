package com.yangaobo.servicedesk.user.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.error.AccountErrorCode;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;
import com.yangaobo.servicedesk.user.mapper.AccountMapper;
import com.yangaobo.servicedesk.user.mapper.AdminEventMapper;
import com.yangaobo.servicedesk.user.mapper.OrganizationMapper;
import com.yangaobo.servicedesk.user.model.enums.RoleEnum;
import com.yangaobo.servicedesk.user.service.IAuthenticationService;
import com.yangaobo.servicedesk.user.service.IIdentityAdministrationService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * 身份管理授权与审计的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityAdministrationServiceImpl implements IIdentityAdministrationService {
    private final OrganizationMapper organizationMapper;
    private final AccountMapper accountMapper;
    private final AdminEventMapper adminEventMapper;
    private final IAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAdministrator(IdentityDTO actor) {
        if (organizationMapper.lockProvider() == null)
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        requireAdministrator(actor);
    }

    /** {@inheritDoc} */
    @Override
    public void requireAdministrator(IdentityDTO actor) {
        IdentityDTO current =
                authenticationService.verify(
                        actor.getOwnerId(), actor.getAuthVersion(), actor.getSessionRef());
        if (current.getMustChangePassword())
            throw new BusinessException(AuthenticationErrorCode.PASSWORD_CHANGE_REQUIRED);
        if (!current.getRoles().contains(RoleEnum.SYSTEM_ADMIN.getValue()))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    /** {@inheritDoc} */
    @Override
    public void ensureAdministrator() {
        if (accountMapper.activeAdministrators() == 0)
            throw new BusinessException(AccountErrorCode.LAST_ADMINISTRATOR);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(
            String actor,
            String user,
            String organization,
            String type,
            Object before,
            Object after,
            String reason) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("schema_version", 1);
        details.set("before", objectMapper.valueToTree(before));
        details.set("after", objectMapper.valueToTree(after));
        details.put("reason", reason);
        adminEventMapper.append(
                UUID.randomUUID().toString().replace("-", ""),
                actor == null ? "BOOTSTRAP" : "USER",
                actor,
                user,
                organization,
                type,
                details.toString());
    }

    /** {@inheritDoc} */
    @Override
    public void validateRoles(String organizationType, Set<String> roles) {
        Set<String> staff =
                Set.of(
                        RoleEnum.SERVICE_AGENT.getValue(),
                        RoleEnum.TECHNICIAN.getValue(),
                        RoleEnum.KNOWLEDGE_ADMIN.getValue(),
                        RoleEnum.SYSTEM_ADMIN.getValue());
        boolean valid =
                "CUSTOMER".equals(organizationType)
                        ? Set.of(RoleEnum.CUSTOMER_USER.getValue()).equals(roles)
                        : roles != null && !roles.isEmpty() && staff.containsAll(roles);
        if (!valid) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    /** {@inheritDoc} */
    @Override
    public void validatePassword(String password) {
        com.yangaobo.servicedesk.user.security.PasswordPolicy.validate(password);
    }
}
