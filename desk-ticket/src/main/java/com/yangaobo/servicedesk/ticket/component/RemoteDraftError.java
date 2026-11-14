package com.yangaobo.servicedesk.ticket.component;

/**
 * AI所有者已登记的草稿错误在调用边界的安全转换。
 *
 * @author yangaobo
 */
public enum RemoteDraftError implements com.yangaobo.servicedesk.common.base.ErrorCode {
    /** 只接受可信AI标准RPC详情的5201。 */
    DRAFT_EXPIRED;
    /** {@inheritDoc} */
    @Override
    public int code(){return 5201;}
    /** {@inheritDoc} */
    @Override
    public String message(){return "动作草稿已过期，请重新核对";}
}
