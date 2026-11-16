package com.yangaobo.servicedesk.knowledge.component;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 经服务认证的ticket支持范围拒绝，不把依赖失败误判成空知识。
 *
 * @author yangaobo
 */
public enum RemoteSupportError implements ErrorCode {
    /**
     * 新自助请求没有当前服务资格。
     */
    SERVICE_NOT_COVERED;

    /**
     * {@inheritDoc}
     */
    @Override
    public int code() {
        return 3001;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String message() {
        return "客户当前未开通有效支持服务";
    }
}
