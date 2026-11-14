package com.yangaobo.servicedesk.common.base;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/**
 * 表达普通JSON结果，不读取请求、线程上下文或框架状态。
 *
 * @param <T> 安全输出数据类型
 * @author yangaobo
 */
@Getter
public final class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 唯一成功业务码。
     */
    public static final int SUCCESS_CODE = 0;

    /**
     * 稳定业务码，与HTTP状态独立。
     */
    private final int code;

    /**
     * 可公开展示的中文说明。
     */
    private final String message;

    /**
     * 授权后的有效载荷；无数据或失败时为null。
     */
    private final T data;

    /**
     * 请求适配层补充的追踪标识。
     */
    private final String traceId;

    /**
     * 固定结果字段，防止构造后跨请求修改。
     *
     * @param code 业务码
     * @param message 安全说明
     * @param data 有效载荷
     * @param traceId 请求追踪标识
     */
    @JsonCreator
    private Result(@JsonProperty("code") int code, @JsonProperty("message") String message,
                   @JsonProperty("data") T data, @JsonProperty("traceId") String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    /**
     * 创建有载荷的成功结果，追踪标识由入口补充。
     *
     * @param data 授权后的输出
     * @param <T> 输出类型
     * @return 成功业务码为0的结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "操作成功", data, null);
    }

    /**
     * 创建无有效载荷的成功结果。
     *
     * @return data为null的成功结果
     */
    public static Result<Void> success() {
        return success(null);
    }

    /**
     * 创建已在入口登记映射的安全错误。
     *
     * @param error 入口确认可信的错误定义
     * @param traceId 请求追踪标识
     * @return 无有效载荷的非零错误结果
     */
    public static Result<Void> failure(ErrorCode error, String traceId) {
        Objects.requireNonNull(error, "错误定义不能为空");
        if (error.code() <= SUCCESS_CODE) {
            throw new IllegalArgumentException("错误结果必须使用非零正数编号");
        }
        return new Result<>(error.code(), error.message(), null, traceId);
    }

    /**
     * 返回补充追踪标识后的新结果，保留原有效载荷。
     *
     * @param traceId 入口生成或接续的追踪标识
     * @return 新的结果对象
     */
    public Result<T> withTraceId(String traceId) {
        return new Result<>(code, message, data, Objects.requireNonNull(traceId));
    }
}
