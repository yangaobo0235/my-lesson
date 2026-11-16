package com.yangaobo.servicedesk.knowledge.security;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.knowledge.component.RemoteIdentityError;
import jakarta.servlet.http.HttpServletRequest;
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
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class CurrentActor {
    private final HttpServletRequest request;
    private final IdentityVerificationClient identityClient;

    /**
     * 每次浏览器请求重新核验身份，忽略任何客户端身份声明头。
     */
    public IdentityDTO requireIdentity() {
        VerifyBrowserForm form = new VerifyBrowserForm();
        String cookie = request.getHeader("Cookie");
        if (cookie == null || cookie.isBlank())
            throw new BusinessException(RemoteIdentityError.UNAUTHENTICATED);
        if (cookie.length() > 8192) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        form.setCookie(cookie);
        form.setMethod(request.getMethod());
        form.setCsrfToken(request.getHeader("X-CSRF-TOKEN"));
        return identityClient.verifyBrowser(form);
    }
}
