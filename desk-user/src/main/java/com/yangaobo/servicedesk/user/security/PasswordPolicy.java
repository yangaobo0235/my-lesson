package com.yangaobo.servicedesk.user.security;

import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;

import java.nio.charset.StandardCharsets;

/**
 * 用户改密的业务边界。
 *
 * @author yangaobo
 */
public final class PasswordPolicy {
    /** 禁止创建无状态规则实例。 */
    private PasswordPolicy() {}

    /** 校验BCrypt的72字节上限及最低强度。 */
    public static void validate(String password) {
        int size = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (size < 12
                || size > 72
                || !password.matches("(?s).*[A-Za-z].*")
                || !password.matches("(?s).*[0-9].*")) {
            throw new BusinessException(AuthenticationErrorCode.INVALID_PASSWORD);
        }
    }
}
