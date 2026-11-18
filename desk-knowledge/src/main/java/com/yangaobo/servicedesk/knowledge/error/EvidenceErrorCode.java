package com.yangaobo.servicedesk.knowledge.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 已有读取权限的知识来源风险拒绝，由知识所有者判定。
 *
 * @author yangaobo
 */
public enum EvidenceErrorCode implements ErrorCode {
    /**
     * 有权审阅的历史资料因安全风险撤回，不再交付指导。
     */
    SOURCE_WITHDRAWN(5103, "来源已因风险撤回，请重新取得有效依据");

    /**
     * 稳定编号。
     */
    private final int code;
    /**
     * 可公开提示。
     */
    private final String message;

    /**
     * 固定错误编号与安全说明。
     */
    EvidenceErrorCode(int code, String message) {
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
