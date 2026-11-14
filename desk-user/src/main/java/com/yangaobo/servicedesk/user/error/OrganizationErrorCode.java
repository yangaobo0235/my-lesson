package com.yangaobo.servicedesk.user.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 组织管理错误的业务边界。
 *
 * @author yangaobo
 */
public enum OrganizationErrorCode implements ErrorCode {
    /** 组织当前不可用。 */
    ORGANIZATION_UNAVAILABLE(2201, "组织当前不可用");

    /** 稳定错误码。 */
    private final int code;

    /** 安全错误提示。 */
    private final String message;

    /** 固定错误合同。 */
    OrganizationErrorCode(int code, String message) {
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
