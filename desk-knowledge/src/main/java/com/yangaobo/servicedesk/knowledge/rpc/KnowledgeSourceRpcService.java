package com.yangaobo.servicedesk.knowledge.rpc;

import com.yangaobo.servicedesk.api.knowledge.v1.KnowledgeSourceServiceGrpc;
import com.yangaobo.servicedesk.api.knowledge.v1.SourceQuery;
import com.yangaobo.servicedesk.api.knowledge.v1.VerifySourcesRequest;
import com.yangaobo.servicedesk.api.knowledge.v1.AuthorizedSources;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeAccessService;
import io.grpc.stub.StreamObserver;

/**
 * 授权召回和交付复核共用当前发布与适用规则。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeSourceRpcService extends KnowledgeSourceServiceGrpc.KnowledgeSourceServiceImplBase {
    private final KnowledgeAccessService knowledgeAccessService;
    /** 查询前过滤的任务集合由knowledge唯一授权。 */
    @Override
    public void queryAuthorizedSources(SourceQuery request,StreamObserver<AuthorizedSources> observer){RpcResponses.respond(observer,()->knowledgeAccessService.authorized(request));}
    /** 全部实际依赖重新核验，不能只核验引用子集。 */
    @Override
    public void verifySources(VerifySourcesRequest request,StreamObserver<AuthorizedSources> observer){RpcResponses.respond(observer,()->knowledgeAccessService.verify(request.getContext(),request.getSourcesList()));}
}
