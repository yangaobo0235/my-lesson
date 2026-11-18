package com.yangaobo.servicedesk.ticket.rpc;

import com.google.protobuf.ByteString;
import com.yangaobo.servicedesk.api.ticket.v1.DiagnosticAttachmentServiceGrpc;
import com.yangaobo.servicedesk.api.ticket.v1.RedactedPart;
import com.yangaobo.servicedesk.api.ticket.v1.RedactedRequest;
import com.yangaobo.servicedesk.api.ticket.v1.AttachmentListRequest;
import com.yangaobo.servicedesk.api.ticket.v1.AttachmentList;
import com.yangaobo.servicedesk.api.ticket.v1.AttachmentReference;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.component.web.HttpErrorMapping;
import com.yangaobo.servicedesk.ticket.model.dto.attachment.RedactedAttachment;
import com.yangaobo.servicedesk.ticket.service.IAttachmentService;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 按原身份读取固定脱敏版本；流完成前重核授权，部分内容不算成功。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class DiagnosticAttachmentRpcService extends DiagnosticAttachmentServiceGrpc.DiagnosticAttachmentServiceImplBase {
    /**
     * 当前身份由user核验，服务凭据不能替代用户范围。
     */
    private final IdentityVerificationClient identities;
    /**
     * 附件状态和授权唯一用例。
     */
    private final IAttachmentService attachments;

    /**
     * 沿当前身份发现有权脱敏版本；候选元信息不代表已读取正文。
     */
    @Override
    public void listRedacted(AttachmentListRequest request, StreamObserver<AttachmentList> observer) {
        try {
            var form = new VerifySessionForm();
            form.setOwnerId(request.getActor().getOwnerId());
            form.setSessionRef(request.getActor().getSessionRef());
            form.setAuthVersion(request.getActor().getAuthVersion());
            var values = attachments.listRedacted(identities.verifySession(form), request.getTicketId(),
                    request.hasAfterId() ? request.getAfterId() : null);
            var response = AttachmentList.newBuilder();
            for (var value : values.stream().limit(20).toList()) {
                response.addItems(AttachmentReference.newBuilder().setId(value.getId())
                        .setName(value.getOriginalFileName()).setContentFormat(value.getContentFormat())
                        .setRedactionVersion(value.getRedactionVersion()).setSha256(value.getRedactedSha256()));
            }
            if (values.size() > 20) response.setNextAfterId(values.get(19).getId());
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (BusinessException failure) {
            RpcResponses.fail(observer, HttpErrorMapping.registered(failure.getErrorCode()));
        } catch (Exception failure) {
            RpcResponses.fail(observer, CommonErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 按就绪回调发送，取消释放正文引用，不创建第二个线程或无限队列。
     */
    @Override
    public void readRedacted(RedactedRequest request, StreamObserver<RedactedPart> observer) {
        var outbound = (ServerCallStreamObserver<RedactedPart>) observer;
        if (outbound.isCancelled()) return;
        try {
            Supplier<RedactedAttachment> read = () -> read(request);
            var transfer = new RedactedTransfer(outbound, read.get(), read);
            outbound.setOnCancelHandler(transfer::cancel);
            outbound.setOnReadyHandler(transfer::drain);
            transfer.drain();
        } catch (BusinessException failure) {
            if (!outbound.isCancelled())
                RpcResponses.fail(observer, HttpErrorMapping.registered(failure.getErrorCode()));
        } catch (Exception failure) {
            if (!outbound.isCancelled()) RpcResponses.fail(observer, CommonErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 首次和完成前都沿同一原身份重核，不使用缓存角色。
     */
    private RedactedAttachment read(RedactedRequest request) {
        var form = new VerifySessionForm();
        form.setOwnerId(request.getActor().getOwnerId());
        form.setSessionRef(request.getActor().getSessionRef());
        form.setAuthVersion(request.getActor().getAuthVersion());
        return attachments.readRedacted(identities.verifySession(form), request.getTicketId(),
                request.getAttachmentId(), request.getRedactionVersion());
    }

    /**
     * 单次传输持有至多1MiB正文，回调串行化偏移及终态。
     *
     * @author yangaobo
     */
    private static final class RedactedTransfer {
        /**
         * 当前传输观察者。
         */
        private final ServerCallStreamObserver<RedactedPart> observer;
        /**
         * 完成前重新读取固定版本及当前权限。
         */
        private final Supplier<RedactedAttachment> recheck;
        /**
         * 终止立即释放正文，不保留外部存储句柄。
         */
        private RedactedAttachment value;
        /**
         * 下一个字节偏移。
         */
        private int offset;
        /**
         * 元信息仅发送一次。
         */
        private boolean metadataSent;

        /**
         * 绑定本次请求的有界资源。
         */
        private RedactedTransfer(ServerCallStreamObserver<RedactedPart> observer,
                                 RedactedAttachment value, Supplier<RedactedAttachment> recheck) {
            this.observer = observer;
            this.value = value;
            this.recheck = recheck;
        }

        /**
         * 取消不发送完成，不改变任何领域状态。
         */
        private synchronized void cancel() {
            value = null;
        }

        /**
         * 元信息无正文，后续严格连续；撤权或换版使最终RPC失败。
         */
        private synchronized void drain() {
            if (value == null) return;
            try {
                while (observer.isReady() && !observer.isCancelled()) {
                    if (!metadataSent) {
                        observer.onNext(RedactedPart.newBuilder().setOffset(0)
                                .setTotalBytes(value.getBytes().length).setSha256(value.getSha256())
                                .setContentFormat(value.getContentFormat())
                                .setRedactionVersion(value.getRedactionVersion()).build());
                        metadataSent = true;
                    } else if (offset < value.getBytes().length) {
                        int size = Math.min(65536, value.getBytes().length - offset);
                        observer.onNext(RedactedPart.newBuilder().setOffset(offset)
                                .setData(ByteString.copyFrom(value.getBytes(), offset, size)).build());
                        offset += size;
                    } else {
                        var current = recheck.get();
                        if (current.getRedactionVersion() != value.getRedactionVersion()
                                || !current.getSha256().equals(value.getSha256())
                                || !current.getContentFormat().equals(value.getContentFormat()))
                            throw new BusinessException(CommonErrorCode.FORBIDDEN);
                        cancel();
                        if (!observer.isCancelled()) observer.onCompleted();
                        return;
                    }
                }
                if (observer.isCancelled()) cancel();
            } catch (BusinessException failure) {
                cancel();
                if (!observer.isCancelled())
                    RpcResponses.fail(observer, HttpErrorMapping.registered(failure.getErrorCode()));
            } catch (Exception failure) {
                cancel();
                if (!observer.isCancelled()) RpcResponses.fail(observer, CommonErrorCode.INTERNAL_ERROR);
            }
        }
    }
}
