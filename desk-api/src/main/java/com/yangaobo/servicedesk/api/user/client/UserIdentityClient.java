package com.yangaobo.servicedesk.api.user.client;

import com.yangaobo.servicedesk.api.user.dto.EligibilityDTO;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.EligibilityForm;
import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.common.base.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * UserIdentityClient的业务合同。
 *
 * @author yangaobo
 */
@FeignClient(name = "desk-user", contextId = "userIdentity", url = "${desk.identity.user-url:}")
public interface UserIdentityClient {
    /**
     * 核验浏览器身份及写入CSRF，只向身份所有者发送原Cookie。
     */
    @PostMapping("/internal/v1/auth/verify")
    Result<IdentityDTO> verify(
            @RequestHeader("Authorization") String authorization,
            @RequestBody VerifyBrowserForm form);

    /**
     * 重核不可登录会话引用。
     */
    @PostMapping("/internal/v1/auth/verify-session")
    Result<IdentityDTO> verifySession(
            @RequestHeader("Authorization") String authorization,
            @RequestBody VerifySessionForm form);

    /**
     * 在当前人员范围内核验客户和可选分派人。
     */
    @PostMapping("/internal/v1/auth/check-eligibility")
    Result<EligibilityDTO> eligibility(
            @RequestHeader("Authorization") String authorization,
            @RequestBody EligibilityForm form);
}
