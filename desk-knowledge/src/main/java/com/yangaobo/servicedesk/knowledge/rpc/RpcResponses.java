package com.yangaobo.servicedesk.knowledge.rpc;

import com.google.protobuf.Any;
import com.google.rpc.Status;
import com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail;
import com.yangaobo.servicedesk.api.rpc.RpcContract;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.knowledge.component.web.HttpErrorMapping;
import io.grpc.Context;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 所属RPC边界将安全业务错误转换为标准状态详情。
 *
 * @author yangaobo
 */
public final class RpcResponses {
    /**
     * 不创建无状态工具实例。
     */
    private RpcResponses() {
    }

    /**
     * 已取消调用不再交付；只捕获业务执行异常，不递归调用观察者。
     */
    public static <T> void respond(StreamObserver<T> observer, Supplier<T> operation) {
        if (Context.current().isCancelled()) {
            return;
        }
        T value;
        try {
            value = operation.get();
        } catch (BusinessException failure) {
            fail(observer, HttpErrorMapping.registered(failure.getErrorCode()));
            return;
        } catch (Exception failure) {
            fail(observer, CommonErrorCode.INTERNAL_ERROR);
            return;
        }
        if (!Context.current().isCancelled()) {
            observer.onNext(value);
            observer.onCompleted();
        }
    }

    /**
     * 保留安全数字详情与追踪号，系统异常明确映射INTERNAL。
     */
    private static void fail(StreamObserver<?> observer, ErrorCode error) {
        if (Context.current().isCancelled()) {
            return;
        }
        var detail =
                RpcErrorDetail.newBuilder()
                        .setCode(error.code())
                        .setMessage(error.message())
                        .setTraceId(
                                Optional.ofNullable(DomainRpcInterceptor.TRACE_ID.get())
                                        .filter(value -> !value.isBlank())
                                        .orElseGet(IdentifierGenerator::next))
                        .build();
        observer.onError(
                StatusProto.toStatusRuntimeException(
                        Status.newBuilder()
                                .setCode(RpcContract.status(error.code()).getCode().value())
                                .setMessage(error.message())
                                .addDetails(Any.pack(detail))
                                .build()));
    }
}
