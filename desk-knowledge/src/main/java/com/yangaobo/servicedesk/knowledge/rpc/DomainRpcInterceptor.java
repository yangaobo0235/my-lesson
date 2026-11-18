package com.yangaobo.servicedesk.knowledge.rpc;

import com.alibaba.csp.sentinel.AsyncEntry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.yangaobo.servicedesk.api.rpc.RpcContract;
import com.yangaobo.servicedesk.knowledge.security.DomainServiceCredentials;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Deadline;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TLS之后逐次核验AI凭据、方法白名单与有界Deadline。
 *
 * @author yangaobo
 */
public final class DomainRpcInterceptor implements ServerInterceptor {
    /**
     * 仅开放实际领域方法，不开放反射或任意管理动作。
     */
    private static final Set<String> METHODS =
            Set.of(
                    "servicedesk.knowledge.v1.KnowledgeSourceService/QueryAuthorizedSources",
                    "servicedesk.knowledge.v1.KnowledgeSourceService/VerifySources",
                    "servicedesk.knowledge.v1.KnowledgeEvidenceService/ReadProcedure",
                    "servicedesk.knowledge.v1.OriginalFileService/ReadOriginal");

    /**
     * metadata中的独立服务凭据。
     */
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * 供安全错误返回的当前RPC追踪号。
     */
    public static final Context.Key<String> TRACE_ID = Context.key("knowledge-trace-id");

    /**
     * 全局在途上限，饱和时立即拒绝，不占用数据库等待队列。
     */
    private final Semaphore inFlight = new Semaphore(32);

    /**
     * 使用应用现有W3C传播器与追踪生命周期。
     */
    private final Propagator propagator;

    /**
     * 应用追踪器，禁用采样仍保持上下文。
     */
    private final Tracer tracer;

    /**
     * 当前有效服务凭据，不使用浏览器领域头。
     */
    private final DomainServiceCredentials credentials;

    /**
     * 注入user维护的调用方凭据。
     */
    public DomainRpcInterceptor(
            DomainServiceCredentials credentials, Propagator propagator, Tracer tracer) {
        this.credentials = credentials;
        this.propagator = propagator;
        this.tracer = tracer;
    }

    /**
     * 未认证请求不附会话失效详情，调用方必须视为依赖认证故障。
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!credentials.isAi(headers.get(AUTHORIZATION))
                || !METHODS.contains(call.getMethodDescriptor().getFullMethodName())) {
            call.close(Status.PERMISSION_DENIED.withDescription("调用服务未获授权"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        Deadline deadline = Context.current().getDeadline();
        // grpc-timeout按有效数字取整，容许10毫秒线编码误差；调用方预留100毫秒交付预算。
        if (deadline == null
                || deadline.timeRemaining(TimeUnit.MILLISECONDS)
                > (RpcContract.maximumMillis(call.getMethodDescriptor().getFullMethodName())
                + 10)) {
            call.close(
                    Status.INVALID_ARGUMENT.withDescription("领域调用必须声明合同范围内的Deadline"),
                    new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        if (!inFlight.tryAcquire()) {
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("领域核验繁忙"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        AsyncEntry entry;
        try {
            entry = SphU.asyncEntry("knowledge:rpc:domain", EntryType.IN);
        } catch (BlockException limited) {
            inFlight.release();
            call.close(Status.UNAVAILABLE.withDescription("领域核验暂不可用"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        Span span =
                propagator
                        .extract(
                                headers,
                                (carrier, name) ->
                                        carrier.get(
                                                Metadata.Key.of(
                                                        name, Metadata.ASCII_STRING_MARSHALLER)))
                        .kind(Span.Kind.SERVER)
                        .name("knowledge.domain.rpc")
                        .start();
        String trace = span.context().traceId();
        if (trace == null || trace.isBlank()) trace = UUID.randomUUID().toString().replace("-", "");
        Context context = Context.current().withValue(TRACE_ID, trace);
        AtomicBoolean ended = new AtomicBoolean();
        Runnable finish =
                () -> {
                    if (ended.compareAndSet(false, true)) {
                        try {
                            entry.exit();
                        } finally {
                            span.end();
                            inFlight.release();
                        }
                    }
                };
        ServerCall<ReqT, RespT> observed =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    /** 真实RPC结束才释放限流条目，不以异步调度返回代替完成。 */
                    @Override
                    public void close(Status status, Metadata trailers) {
                        try {
                            if (status.getCode() == Status.Code.INTERNAL
                                    || status.getCode() == Status.Code.UNAVAILABLE) {
                                span.error(status.asRuntimeException());
                                com.alibaba.csp.sentinel.Tracer.traceEntry(
                                        status.asRuntimeException(), entry);
                            }
                            super.close(status, trailers);
                        } finally {
                            finish.run();
                        }
                    }
                };
        try (var scope = tracer.withSpan(span)) {
            ServerCall.Listener<ReqT> delegate =
                    Contexts.interceptCall(context, observed, headers, next);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
                /** 回调线程恢复调用链，领域核验和数据库访问属于同一span。 */
                @Override
                public void onHalfClose() {
                    try (var ignored = tracer.withSpan(span)) {
                        super.onHalfClose();
                    } catch (RuntimeException failure) {
                        span.error(failure);
                        finish.run();
                        throw failure;
                    }
                }

                /** 客户端取消立即释放在途资源。 */
                @Override
                public void onCancel() {
                    try {
                        super.onCancel();
                    } finally {
                        finish.run();
                    }
                }

                /** 正常完成兜底释放，仅执行一次。 */
                @Override
                public void onComplete() {
                    try {
                        super.onComplete();
                    } finally {
                        finish.run();
                    }
                }
            };
        } catch (RuntimeException failure) {
            span.error(failure);
            finish.run();
            throw failure;
        }
    }
}
