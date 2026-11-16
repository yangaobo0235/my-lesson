package com.yangaobo.servicedesk.common.exception;

import com.yangaobo.servicedesk.common.base.ErrorCode;

import java.io.Serial;
import java.util.Objects;

import lombok.Getter;

/**
 * 表达安全业务拒绝，具体传输状态由入口决定。
 *
 * @author yangaobo
 */
@Getter
public final class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需由边界确认已登记的错误定义。
     */
    private final ErrorCode errorCode;

    /**
     * 保留稳定错误，不接收任意外部消息。
     *
     * @param errorCode 业务错误定义
     */
    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).message());
        this.errorCode = errorCode;
    }
}
