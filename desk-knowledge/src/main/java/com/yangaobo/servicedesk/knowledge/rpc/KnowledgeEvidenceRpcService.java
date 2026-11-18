package com.yangaobo.servicedesk.knowledge.rpc;

import com.yangaobo.servicedesk.api.knowledge.v1.KnowledgeEvidenceServiceGrpc;
import com.yangaobo.servicedesk.api.knowledge.v1.ProcedureRequest;
import com.yangaobo.servicedesk.api.knowledge.v1.ReviewedProcedure;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeAccessService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 规程读取只返回已审核的有权固定修订，不开放独立规程写入。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeEvidenceRpcService extends KnowledgeEvidenceServiceGrpc.KnowledgeEvidenceServiceImplBase {
    private final KnowledgeAccessService access;

    /**
     * 当前授权与摘要一致后交付不可变规程。
     */
    @Override
    public void readProcedure(ProcedureRequest request, StreamObserver<ReviewedProcedure> observer) {
        RpcResponses.respond(observer, () -> access.procedure(request));
    }
}
