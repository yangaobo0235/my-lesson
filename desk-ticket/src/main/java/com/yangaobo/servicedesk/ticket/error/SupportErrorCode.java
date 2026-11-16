package com.yangaobo.servicedesk.ticket.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 服务资格错误的业务边界。
 *
 * @author yangaobo
 */
public enum SupportErrorCode implements ErrorCode {
    /**
     * 客户当前未开通有效支持服务。
     */
    SERVICE_NOT_COVERED(3001, "客户当前未开通有效支持服务");

    /**
     * 稳定错误码。
     */
    private final int code;

    /**
     * 安全提示。
     */
    private final String message;

    /**
     * 固定错误合同。
     */
    SupportErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int code() {
        return code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String message() {
        return message;
    }
}
