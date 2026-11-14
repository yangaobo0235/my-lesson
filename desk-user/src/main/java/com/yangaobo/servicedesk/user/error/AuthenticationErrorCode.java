package com.yangaobo.servicedesk.user.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 登录与会话错误的业务边界。
 *
 * @author yangaobo
 */
public enum AuthenticationErrorCode implements ErrorCode {
    /** 请先登录。 */
    UNAUTHENTICATED(2001, "请先登录"),
    /** 登录状态已失效，请重新登录。 */
    SESSION_EXPIRED(2002, "登录状态已失效，请重新登录"),
    /** 账号或密码错误。 */
    INVALID_CREDENTIALS(2003, "账号或密码错误"),
    /** 请先修改密码。 */
    PASSWORD_CHANGE_REQUIRED(2004, "请先修改密码"),
    /** 密码须为12至72字节且包含字母和数字。 */
    INVALID_PASSWORD(2005, "密码须为12至72字节且包含字母和数字"),
    /** 登录尝试过多，请稍后重试。 */
    TOO_MANY_ATTEMPTS(2006, "登录尝试过多，请稍后重试");

    /** 稳定错误码。 */
    private final int code;

    /** 安全错误提示。 */
    private final String message;

    /** 固定错误合同。 */
    AuthenticationErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /** {@inheritDoc} */
    @Override
    public int code() {
        return code;
    }

    /** {@inheritDoc} */
    @Override
    public String message() {
        return message;
    }
}
