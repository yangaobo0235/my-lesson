package com.yangaobo.servicedesk.user.service.impl;

import com.yangaobo.servicedesk.api.user.dto.EligibilityDTO;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.EligibilityForm;
import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;
import com.yangaobo.servicedesk.user.mapper.AccountMapper;
import com.yangaobo.servicedesk.user.mapper.OrganizationMapper;
import com.yangaobo.servicedesk.user.model.entity.AccountEntity;
import com.yangaobo.servicedesk.user.model.entity.OrganizationEntity;
import com.yangaobo.servicedesk.user.model.enums.RoleEnum;
import com.yangaobo.servicedesk.user.security.SessionCsrfTokenRepository;
import com.yangaobo.servicedesk.user.service.IAuthenticationService;
import com.yangaobo.servicedesk.user.service.IIdentityVerificationService;

import java.time.Instant;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;

/**
 * 内部身份核验的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityVerificationServiceImpl implements IIdentityVerificationService {
    private final JdbcIndexedSessionRepository sessions;
    private final IAuthenticationService authenticationService;
    private final AccountMapper accountMapper;
    private final OrganizationMapper organizationMapper;
    private final SessionCsrfTokenRepository csrfTokens;

    /**
     * {@inheritDoc}
     */
    @Override
    public IdentityDTO verifyBrowser(VerifyBrowserForm form) {
        String id = null;
        for (String part : form.getCookie().split(";")) {
            String[] pair = part.strip().split("=", 2);
            if (pair.length == 2 && "DESK_SESSION".equals(pair[0])) {
                if (id != null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                id = pair[1];
            }
        }
        if (id == null) throw new BusinessException(AuthenticationErrorCode.UNAUTHENTICATED);
        var session = sessions.findById(id);
        IdentityDTO actor = identity(session, accountMapper.sessionRef(id));
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(form.getMethod())
                && !csrfTokens.matches(id, form.getCsrfToken()))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        ((Session) session).setLastAccessedTime(Instant.now());
        sessions.save(session);
        return actor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IdentityDTO verifySession(VerifySessionForm form) {
        String id = accountMapper.sessionId(form.getSessionRef());
        IdentityDTO actor =
                identity(id == null ? null : sessions.findById(id), form.getSessionRef());
        if (!actor.getOwnerId().equals(form.getOwnerId())
                || !actor.getAuthVersion().equals(form.getAuthVersion()))
            throw new BusinessException(AuthenticationErrorCode.SESSION_EXPIRED);
        return actor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EligibilityDTO eligibility(EligibilityForm form) {
        IdentityDTO actor = verifySession(form);
        boolean customer =
                "CUSTOMER".equals(actor.getOrganizationType())
                        && actor.getOrganizationId().equals(form.getCustomerId());
        boolean staff =
                (actor.getRoles().contains(RoleEnum.SERVICE_AGENT.getValue())
                        || actor.getRoles().contains(RoleEnum.TECHNICIAN.getValue()))
                        && actor.getCustomerIds().contains(form.getCustomerId());
        if (!customer && !staff) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        OrganizationEntity organization = organizationMapper.selectById(form.getCustomerId());
        EligibilityDTO result = new EligibilityDTO();
        result.setCustomerActive(
                organization != null
                        && "CUSTOMER".equals(organization.getOrganizationType())
                        && "ACTIVE".equals(organization.getStatus()));
        if (form.getAssigneeId() != null) {
            if (!staff) throw new BusinessException(CommonErrorCode.FORBIDDEN);
            AccountEntity assignee = accountMapper.selectById(form.getAssigneeId());
            OrganizationEntity team =
                    assignee == null
                            ? null
                            : organizationMapper.selectById(assignee.getOrganizationId());
            result.setAssigneeAvailable(
                    assignee != null
                            && "ACTIVE".equals(assignee.getStatus())
                            && team != null
                            && "SERVICE_PROVIDER".equals(team.getOrganizationType())
                            && "ACTIVE".equals(team.getStatus())
                            && !assignee.getMustChangePassword()
                            && accountMapper
                            .roles(assignee.getId())
                            .contains(RoleEnum.TECHNICIAN.getValue())
                            && accountMapper
                            .scopes(assignee.getId())
                            .contains(form.getCustomerId()));
        }
        return result;
    }

    /**
     * Session仅提供拥有者与版本，其余身份来自数据库。
     */
    private IdentityDTO identity(Session session, String reference) {
        if (session == null
                || session.isExpired()
                || reference == null
                || !(session.getAttribute("ownerId") instanceof String owner)
                || !(session.getAttribute("authVersion") instanceof Long version))
            throw new BusinessException(AuthenticationErrorCode.SESSION_EXPIRED);
        IdentityDTO actor = authenticationService.verify(owner, version, reference);
        if (actor.getMustChangePassword())
            throw new BusinessException(AuthenticationErrorCode.PASSWORD_CHANGE_REQUIRED);
        return actor;
    }
}
