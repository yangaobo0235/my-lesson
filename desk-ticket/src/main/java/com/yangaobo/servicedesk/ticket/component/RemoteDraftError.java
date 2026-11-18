package com.yangaobo.servicedesk.ticket.component;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * AI所有者已登记的草稿错误在调用边界的安全转换。
 *
 * @author yangaobo
 */
public enum RemoteDraftError implements ErrorCode {
    /**
     * 只接受可信AI标准RPC详情的5201。
     */
    DRAFT_EXPIRED(5201, "动作草稿已过期，请重新核对"),
    /**
     * 已使用知识因风险撤回，不能继续采用派生建议。
     */
    SOURCE_WITHDRAWN(5103, "引用资料已因风险撤回，请重新核对");

    /**
     * 远端所有者登记的业务编号。
     */
    private final int code;
    /**
     * 可向用户公开的安全提示。
     */
    private final String message;

    /**
     * 固定可信远端编号与本地安全提示。
     */
    RemoteDraftError(int code, String message) {
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
