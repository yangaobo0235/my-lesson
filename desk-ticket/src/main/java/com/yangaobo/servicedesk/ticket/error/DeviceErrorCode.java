package com.yangaobo.servicedesk.ticket.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 设备关联错误的业务边界。
 *
 * @author yangaobo
 */
public enum DeviceErrorCode implements ErrorCode {
    /**
     * 设备不可新增关联。
     */
    DEVICE_INELIGIBLE(3101, "设备不可新增关联"),
    /**
     * 型号不可新增关联。
     */
    MODEL_UNAVAILABLE(3102, "型号不可新增关联");

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
    DeviceErrorCode(int code, String message) {
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
