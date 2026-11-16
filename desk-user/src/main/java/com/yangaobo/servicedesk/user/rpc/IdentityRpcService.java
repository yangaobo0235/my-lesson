package com.yangaobo.servicedesk.user.rpc;

import com.google.protobuf.Any;
import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail;
import com.yangaobo.servicedesk.api.rpc.RpcContract;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.api.user.v1.IdentityServiceGrpc;
import com.yangaobo.servicedesk.api.user.v1.VerifiedIdentity;
import com.yangaobo.servicedesk.api.user.v1.VerifyBrowserSessionRequest;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.component.web.HttpErrorMapping;
import com.yangaobo.servicedesk.user.service.IIdentityVerificationService;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import jakarta.validation.Validator;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 将跨语言请求适配到HTTP共用身份用例，安全错误使用标准详情。
 *
 * @author yangaobo
 */
public final class IdentityRpcService extends IdentityServiceGrpc.IdentityServiceImplBase {
    /**
     * 唯一身份用例。
     */
    private final IIdentityVerificationService identities;

    /**
     * 复用业务表单校验。
     */
    private final Validator validator;

    /**
     * 注入应用拥有的身份用例和校验器。
     */
    public IdentityRpcService(IIdentityVerificationService identities, Validator validator) {
        this.identities = identities;
        this.validator = validator;
    }

    /**
     * 校验原浏览器请求，凭据不进入生成响应。
     */
    @Override
    public void verifyBrowserSession(
            VerifyBrowserSessionRequest request, StreamObserver<VerifiedIdentity> observer) {
        respond(
                observer,
                () -> {
                    VerifyBrowserForm form = new VerifyBrowserForm();
                    form.setCookie(request.getCookie());
                    form.setMethod(request.getMethod());
                    form.setCsrfToken(request.hasCsrfToken() ? request.getCsrfToken() : null);
                    validate(form);
                    return identities.verifyBrowser(form);
                });
    }

    /**
     * 后台任务只能沿原会话引用核验当前身份。
     */
    @Override
    public void verifySessionRef(ActorRef request, StreamObserver<VerifiedIdentity> observer) {
        respond(
                observer,
                () -> {
                    VerifySessionForm form = new VerifySessionForm();
                    form.setSessionRef(request.getSessionRef());
                    form.setOwnerId(request.getOwnerId());
                    form.setAuthVersion(request.getAuthVersion());
                    validate(form);
                    return identities.verifySession(form);
                });
    }

    /**
     * Bean Validation失败统一映射输入错误。
     */
    private void validate(Object form) {
        if (!validator.validate(form).isEmpty())
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 取消后不交付结果；错误详情不包含异常文本或凭据。
     */
    private void respond(
            StreamObserver<VerifiedIdentity> observer, Supplier<IdentityDTO> operation) {
        try {
            if (Context.current().isCancelled()) return;
            IdentityDTO identity = operation.get();
            if (Context.current().isCancelled()) return;
            observer.onNext(
                    VerifiedIdentity.newBuilder()
                            .setActor(
                                    ActorRef.newBuilder()
                                            .setOwnerId(identity.getOwnerId())
                                            .setSessionRef(identity.getSessionRef())
                                            .setAuthVersion(identity.getAuthVersion()))
                            .setOrganizationId(identity.getOrganizationId())
                            .setOrganizationType(identity.getOrganizationType())
                            .setDisplayName(identity.getDisplayName())
                            .addAllRoles(identity.getRoles())
                            .addAllCustomerIds(identity.getCustomerIds())
                            .build());
            observer.onCompleted();
        } catch (BusinessException failure) {
            fail(observer, HttpErrorMapping.registered(failure.getErrorCode()));
        } catch (RuntimeException failure) {
            fail(observer, CommonErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 业务错误映射保留明确HTTP语义，不按错误号段推断。
     */
    private void fail(StreamObserver<?> observer, ErrorCode error) {
        Status.Code code = RpcContract.status(error.code()).getCode();
        String traceId = IdentityRpcInterceptor.TRACE_ID.get();
        if (traceId == null) traceId = UUID.randomUUID().toString().replace("-", "");
        RpcErrorDetail detail =
                RpcErrorDetail.newBuilder()
                        .setCode(error.code())
                        .setMessage(error.message())
                        .setTraceId(traceId)
                        .build();
        observer.onError(
                StatusProto.toStatusRuntimeException(
                        com.google.rpc.Status.newBuilder()
                                .setCode(code.value())
                                .setMessage(error.message())
                                .addDetails(Any.pack(detail))
                                .build()));
    }
}
