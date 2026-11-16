package com.yangaobo.servicedesk.knowledge.component;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 可信身份依赖错误的业务边界。
 *
 * @author yangaobo
 */
public enum RemoteIdentityError implements ErrorCode {
    /**
     * 可信身份服务明确报告未登录。
     */
    UNAUTHENTICATED(2001, "请先登录"),
    /**
     * 可信身份服务明确报告会话失效。
     */
    SESSION_EXPIRED(2002, "登录状态已失效，请重新登录"),
    /**
     * 可信身份服务要求先改密。
     */
    PASSWORD_CHANGE_REQUIRED(2004, "请先修改密码");
    private final int code;
    private final String message;

    /**
     * 仅映射接口约定的user错误，不重新分配编号。
     */
    RemoteIdentityError(int code, String message) {
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
