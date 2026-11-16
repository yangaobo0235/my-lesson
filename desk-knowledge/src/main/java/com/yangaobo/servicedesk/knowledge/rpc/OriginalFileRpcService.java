package com.yangaobo.servicedesk.knowledge.rpc;

import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.google.protobuf.ByteString;
import com.yangaobo.servicedesk.api.knowledge.v1.OriginalFileServiceGrpc;
import com.yangaobo.servicedesk.api.knowledge.v1.OriginalPart;
import com.yangaobo.servicedesk.api.knowledge.v1.OriginalRequest;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.component.OssOriginalStorage;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentMapper;
import com.yangaobo.servicedesk.knowledge.mapper.IndexJobMapper;
import com.yangaobo.servicedesk.knowledge.model.enums.IndexTransactionStateEnum;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI只可按已提交任务关系读取固定原件版本，原件不经浏览器URL中转。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class OriginalFileRpcService extends OriginalFileServiceGrpc.OriginalFileServiceImplBase {
    private final DocumentMapper documents;
    private final IndexJobMapper jobs;
    private final OssOriginalStorage storage;

    /**
     * 元信息和连续字节流成功结束后，接收者才可使用原件。
     */
    @Override
    public void readOriginal(OriginalRequest request, StreamObserver<OriginalPart> observer) {
        var outbound = (ServerCallStreamObserver<OriginalPart>) observer;
        try {
            var job = jobs.selectById(request.getJobId());
            var document = documents.selectById(request.getDocumentId());
            if (job == null
                    || document == null
                    || !job.getDocumentId().equals(document.getId())
                    || !IndexTransactionStateEnum.COMMITTED.getValue().equals(job.getSubmitState()))
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            observer.onNext(
                    OriginalPart.newBuilder()
                            .setOffset(0)
                            .setTotalBytes(document.getFileSizeBytes())
                            .setSha256(document.getFileSha256())
                            .setContentFormat(document.getContentFormat())
                            .build());
            var original = storage.open(document, null);
            if (!document.getFileSizeBytes().equals(original.contentLength())
                    || !document.getStorageVersionId().equals(original.versionId())) {
                original.close();
                throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
            }
            var transfer = new OriginalTransfer(outbound, original, document.getFileSizeBytes());
            outbound.setOnReadyHandler(transfer::drain);
            outbound.setOnCancelHandler(transfer::cancel);
            transfer.drain();
        } catch (Exception failure) {
            observer.onError(Status.UNAVAILABLE.withDescription("原件读取未完整完成").asRuntimeException());
        }
    }

    /**
     * 按传输就绪逐块读取，暂停时返回回调，不在同一RPC串行执行器等待自己。 @author yangaobo
     */
    private static final class OriginalTransfer {
        /**
         * 当前RPC的流控制观察者。
         */
        private final ServerCallStreamObserver<OriginalPart> observer;

        /**
         * 当前精确对象版本的唯一流。
         */
        private final GetObjectResult original;

        /**
         * 不可变字节总量。
         */
        private final long total;

        /**
         * 已成功发送的连续偏移。
         */
        private long offset;

        /**
         * 终止后不再读取或发送。
         */
        private boolean ended;

        /**
         * 绑定单次传输拥有的资源。
         */
        private OriginalTransfer(
                ServerCallStreamObserver<OriginalPart> observer,
                GetObjectResult original,
                long total) {
            this.observer = observer;
            this.original = original;
            this.total = total;
        }

        /**
         * 取消即关闭原件，调用方不把关闭当作成功。
         */
        private synchronized void cancel() {
            close();
        }

        /**
         * 缓冲区固定64KiB；读取、结束和取消串行，关闭最多一次。
         */
        private synchronized void drain() {
            if (ended) return;
            try {
                byte[] buffer = new byte[65536];
                while (observer.isReady() && !observer.isCancelled()) {
                    int count = original.body().read(buffer);
                    if (count < 0) {
                        if (offset != total) throw new IllegalStateException("原件长度不符");
                        close();
                        observer.onCompleted();
                        return;
                    }
                    if (offset + count > total) throw new IllegalStateException("原件长度超限");
                    observer.onNext(
                            OriginalPart.newBuilder()
                                    .setOffset(offset)
                                    .setData(ByteString.copyFrom(buffer, 0, count))
                                    .build());
                    offset += count;
                }
                if (observer.isCancelled()) close();
            } catch (Exception failure) {
                close();
                if (!observer.isCancelled())
                    observer.onError(
                            Status.UNAVAILABLE.withDescription("原件读取未完整完成").asRuntimeException());
            }
        }

        /**
         * 清理本传输资源，不改变任何业务事实。
         */
        private void close() {
            if (ended) return;
            ended = true;
            try {
                original.close();
            } catch (Exception ignored) {
                /* 已结束，交付不再使用原件。 */
            }
        }
    }
}
