package com.yangaobo.servicedesk.knowledge.rpc;

import io.grpc.stub.StreamObserver;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail;
import com.yangaobo.servicedesk.knowledge.component.web.HttpErrorMapping;

/**
 * 所属RPC边界将安全业务错误转换为标准状态详情。
 *
 * @author yangaobo
 */
public final class RpcResponses {
    /** 不创建无状态工具实例。 */
    private RpcResponses() {}
    /** 已取消调用不再交付，未知异常安全回退。 */
    public static <T> void respond(StreamObserver<T> observer,java.util.function.Supplier<T> operation) {
        try {
            if(io.grpc.Context.current().isCancelled())return;
            T value=operation.get();
            if(io.grpc.Context.current().isCancelled())return;
            observer.onNext(value);observer.onCompleted();
        } catch(BusinessException failure) {
            var error=HttpErrorMapping.registered(failure.getErrorCode());
            io.grpc.Status status=switch(error.code()){
                case 1001 -> io.grpc.Status.INVALID_ARGUMENT;
                case 1002 -> io.grpc.Status.NOT_FOUND;
                case 1003,2004 -> io.grpc.Status.PERMISSION_DENIED;
                case 2001,2002 -> io.grpc.Status.UNAUTHENTICATED;
                case 1004,1005,3001,3101,3102,4201 -> io.grpc.Status.FAILED_PRECONDITION;
                case 1013 -> io.grpc.Status.DEADLINE_EXCEEDED;
                default -> io.grpc.Status.UNAVAILABLE;
            };
            var detail=RpcErrorDetail.newBuilder().setCode(error.code()).setMessage(error.message())
                    .setTraceId(java.util.Optional.ofNullable(DomainRpcInterceptor.TRACE_ID.get()).orElse("")).build();
            observer.onError(io.grpc.protobuf.StatusProto.toStatusRuntimeException(com.google.rpc.Status.newBuilder()
                    .setCode(status.getCode().value()).setMessage(error.message()).addDetails(com.google.protobuf.Any.pack(detail)).build()));
        } catch(Exception failure) {observer.onError(io.grpc.Status.INTERNAL.withDescription("领域依赖暂不可用").asRuntimeException());}
    }
}
