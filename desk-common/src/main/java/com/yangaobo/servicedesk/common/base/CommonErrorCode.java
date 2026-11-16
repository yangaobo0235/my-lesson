package com.yangaobo.servicedesk.common.base;

/**
 * 登记公共边界错误；HTTP和RPC状态由各应用显式映射。
 *
 * @author yangaobo
 */
public enum CommonErrorCode implements ErrorCode {

    /**
     * 系统暂时无法完成请求。
     */
    INTERNAL_ERROR(1, "系统暂时无法完成请求"),

    /**
     * 请求参数不合法。
     */
    INVALID_REQUEST(1001, "请求参数不合法"),

    /**
     * 资源不存在或不可见。
     */
    NOT_FOUND(1002, "资源不存在或不可见"),

    /**
     * 当前操作无权限。
     */
    FORBIDDEN(1003, "当前操作无权限"),

    /**
     * 资源状态或版本已变化。
     */
    CONFLICT(1004, "资源状态或版本已变化"),

    /**
     * 业务值已存在。
     */
    DUPLICATE_VALUE(1005, "业务值已存在"),

    /**
     * 请求方法不支持。
     */
    METHOD_NOT_ALLOWED(1006, "请求方法不支持"),

    /**
     * 请求内容超过限制。
     */
    PAYLOAD_TOO_LARGE(1007, "请求内容超过限制"),

    /**
     * 请求内容类型不支持。
     */
    UNSUPPORTED_MEDIA_TYPE(1008, "请求内容类型不支持"),

    /**
     * 请求范围不可满足。
     */
    RANGE_NOT_SATISFIABLE(1009, "请求范围不可满足"),

    /**
     * 请求过于频繁，请稍后重试。
     */
    TOO_MANY_REQUESTS(1010, "请求过于频繁，请稍后重试"),

    /**
     * 服务暂时不可用。
     */
    SERVICE_UNAVAILABLE(1011, "服务暂时不可用"),

    /**
     * 依赖服务响应不符合约定。
     */
    UPSTREAM_PROTOCOL_ERROR(1012, "依赖服务响应不符合约定"),

    /**
     * 依赖服务响应超时。
     */
    DEPENDENCY_TIMEOUT(1013, "依赖服务响应超时");

    /**
     * 接口约定中的唯一错误编号。
     */
    private final int code;

    /**
     * 可公开展示的中文提示。
     */
    private final String message;

    /**
     * 固定公共错误合同。
     *
     * @param code    错误编号
     * @param message 安全中文提示
     */
    CommonErrorCode(int code, String message) {
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
