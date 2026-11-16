package com.yangaobo.servicedesk.user.controller.internal;

import com.yangaobo.servicedesk.api.user.dto.EligibilityDTO;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.EligibilityForm;
import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.user.security.InternalServiceCredentials;
import com.yangaobo.servicedesk.user.service.IIdentityVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部身份核验的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/internal/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityVerificationController {
    private final IIdentityVerificationService identityVerificationService;
    private final InternalServiceCredentials credentials;

    /**
     * 服务认证先于浏览器会话核验，Gateway不发布此路径。
     */
    @Operation(summary = "服务认证先于浏览器会话核验，Gateway不发布此路径。")
    @PostMapping("/verify")
    public Result<IdentityDTO> verify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response,
            @Valid @RequestBody VerifyBrowserForm form) {
        credentials.requireHttp(authorization, false);
        response.setHeader("X-Desk-Identity-Verified", "1");
        IdentityDTO data = identityVerificationService.verifyBrowser(form);
        return Result.success(data);
    }

    /**
     * 重核会话引用，服务凭据本身不授予用户权限。
     */
    @Operation(summary = "重核会话引用，服务凭据本身不授予用户权限。")
    @PostMapping("/verify-session")
    public Result<IdentityDTO> verifySession(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response,
            @Valid @RequestBody VerifySessionForm form) {
        credentials.requireHttp(authorization, false);
        response.setHeader("X-Desk-Identity-Verified", "1");
        IdentityDTO data = identityVerificationService.verifySession(form);
        return Result.success(data);
    }

    /**
     * 当前调用者范围内核验客户及分派人。
     */
    @Operation(summary = "当前调用者范围内核验客户及分派人。")
    @PostMapping("/check-eligibility")
    public Result<EligibilityDTO> eligibility(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response,
            @Valid @RequestBody EligibilityForm form) {
        credentials.requireHttp(authorization, true);
        response.setHeader("X-Desk-Identity-Verified", "1");
        EligibilityDTO data = identityVerificationService.eligibility(form);
        return Result.success(data);
    }
}
