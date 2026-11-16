package com.yangaobo.servicedesk.ticket.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 预约资格的稳定业务拒绝，不替代预约资源的受理结果。
 *
 * @author yangaobo
 */
public enum BookingErrorCode implements ErrorCode {
    /**
     * 当前工单没有预约资格或已存在活动预约。
     */
    BOOKING_NOT_ALLOWED(3301, "工单不满足预约条件或已有活动预约");

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
    BookingErrorCode(int code, String message) {
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
