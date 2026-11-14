package com.yangaobo.servicedesk.user.security;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;
import com.yangaobo.servicedesk.user.mapper.AccountMapper;
import com.yangaobo.servicedesk.user.service.IAuthenticationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 当前请求身份的业务边界。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class CurrentActor {
    private final HttpServletRequest request;
    private final IAuthenticationService authenticationService;
    private final AccountMapper accountMapper;

    /** 每次读取当前账号事实，会话只存身份与版本。 */
    public IdentityDTO requireIdentity() {
        HttpSession session = request.getSession(false);
        if (session == null
                || !(session.getAttribute("ownerId") instanceof String owner)
                || !(session.getAttribute("authVersion") instanceof Long version))
            throw new BusinessException(AuthenticationErrorCode.UNAUTHENTICATED);
        return authenticationService.verify(
                owner, version, accountMapper.sessionRef(session.getId()));
    }

    /** 普通业务禁止绕过强制改密。 */
    public IdentityDTO requireBusinessIdentity() {
        IdentityDTO actor = requireIdentity();
        if (actor.getMustChangePassword())
            throw new BusinessException(AuthenticationErrorCode.PASSWORD_CHANGE_REQUIRED);
        return actor;
    }
}
