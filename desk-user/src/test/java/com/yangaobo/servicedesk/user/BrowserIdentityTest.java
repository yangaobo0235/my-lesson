package com.yangaobo.servicedesk.user;

import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;
import com.yangaobo.servicedesk.user.service.impl.IdentityVerificationServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 浏览器Cookie缺失与内部输入错误的边界回归。
 *
 * @author yangaobo
 */
class BrowserIdentityTest {
    /**
     * 无Cookie通过传输校验后由唯一身份用例返回未登录，不访问数据库。
     */
    @Test
    void missingCookieIsUnauthenticatedRatherThanMalformedInput() {
        VerifyBrowserForm form = new VerifyBrowserForm();
        form.setCookie("");
        form.setMethod("GET");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(form).isEmpty());
        }
        var service = new IdentityVerificationServiceImpl(null, null, null, null, null);
        BusinessException failure = assertThrows(BusinessException.class, () -> service.verifyBrowser(form));
        assertEquals(AuthenticationErrorCode.UNAUTHENTICATED, failure.getErrorCode());
    }

    /**
     * 放行空Cookie不放宽长度、缺失内部字段及HTTP方法白名单。
     */
    @Test
    void malformedInternalEnvelopeStillFailsValidation() {
        VerifyBrowserForm form = new VerifyBrowserForm();
        form.setMethod("GET");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(form).isEmpty());
            form.setCookie("a".repeat(8193));
            assertFalse(validator.validate(form).isEmpty());
            form.setCookie("");
            form.setMethod("EXECUTE");
            assertFalse(validator.validate(form).isEmpty());
        }
    }
}
