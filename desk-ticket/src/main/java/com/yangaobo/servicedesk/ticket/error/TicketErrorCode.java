package com.yangaobo.servicedesk.ticket.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 工单业务错误的业务边界。
 *
 * @author yangaobo
 */
public enum TicketErrorCode implements ErrorCode {
    /**
     * 技术人员当前不具备处理资格。
     */
    ASSIGNEE_UNAVAILABLE(3201, "技术人员当前不具备处理资格");

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
    TicketErrorCode(int code, String message) {
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
