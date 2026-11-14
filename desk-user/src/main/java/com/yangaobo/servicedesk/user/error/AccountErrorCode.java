package com.yangaobo.servicedesk.user.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 账号管理错误的业务边界。
 *
 * @author yangaobo
 */
public enum AccountErrorCode implements ErrorCode {
    /** 必须保留至少一名有效系统管理员。 */
    LAST_ADMINISTRATOR(2102, "必须保留至少一名有效系统管理员");

    /** 稳定错误码。 */
    private final int code;

    /** 安全错误提示。 */
    private final String message;

    /** 固定错误合同。 */
    AccountErrorCode(int code, String message) {
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
