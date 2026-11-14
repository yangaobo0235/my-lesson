package com.yangaobo.servicedesk.user.service;

import com.yangaobo.servicedesk.api.user.dto.EligibilityDTO;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.EligibilityForm;
import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;

/**
 * 内部身份核验的业务边界。
 *
 * @author yangaobo
 */
public interface IIdentityVerificationService {
    /** 从原Cookie解析所属会话，写请求复用同一CSRF规则。 */
    IdentityDTO verifyBrowser(VerifyBrowserForm form);

    /** 由不可登录引用重核账号、原版本及有效会话。 */
    IdentityDTO verifySession(VerifySessionForm form);

    /** 只向当前范围操作者返回必要关联资格。 */
    EligibilityDTO eligibility(EligibilityForm form);
}
