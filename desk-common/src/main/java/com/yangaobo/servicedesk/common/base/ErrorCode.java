package com.yangaobo.servicedesk.common.base;

/**
 * 描述跨入口稳定的错误编号及安全提示，不携带传输状态。
 *
 * @author yangaobo
 */
public interface ErrorCode {

    /**
     * 返回接口约定登记的非零错误编号。
     *
     * @return 稳定业务错误码
     */
    int code();

    /**
     * 返回可向调用者展示的中文提示。
     *
     * @return 不含内部数据的安全说明
     */
    String message();
}
