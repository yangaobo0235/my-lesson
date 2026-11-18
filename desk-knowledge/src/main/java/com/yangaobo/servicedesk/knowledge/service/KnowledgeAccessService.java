package com.yangaobo.servicedesk.knowledge.service;

import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.knowledge.v1.AuthorizedSources;
import com.yangaobo.servicedesk.api.knowledge.v1.KnowledgeSource;
import com.yangaobo.servicedesk.api.knowledge.v1.SourceQuery;
import com.yangaobo.servicedesk.api.ticket.dto.SupportEnvironment;
import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.component.AiIndexClient;
import com.yangaobo.servicedesk.knowledge.error.EvidenceErrorCode;
import com.yangaobo.servicedesk.knowledge.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.knowledge.component.SupportVerificationClient;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentRuleMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentSeriesMapper;
import com.yangaobo.servicedesk.knowledge.mapper.IndexJobMapper;
import com.yangaobo.servicedesk.knowledge.model.enums.IndexTransactionStateEnum;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 当前发布、受众与可信支持环境共同确定知识授权，索引载荷不是权限事实。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeAccessService {
    private final IdentityVerificationClient identities;
    private final SupportVerificationClient support;
    private final DocumentMapper documents;
    private final DocumentRuleMapper rules;
    private final DocumentSeriesMapper series;
    private final IndexJobMapper jobs;
    private final AiIndexClient ai;

    /**
     * 规程复用资料完整授权，未校验、已撤回或无权资料均不得作为执行依据。
     */
    public com.yangaobo.servicedesk.api.knowledge.v1.ReviewedProcedure procedure(
            com.yangaobo.servicedesk.api.knowledge.v1.ProcedureRequest request) {
        var source = authorized(request.getContext()).getSourcesList().stream()
                .filter(item -> item.getDocumentId().equals(request.getDocumentId())).findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
        var document = documents.selectById(source.getDocumentId());
        if (document == null || document.getProcedureDefinition() == null
                || document.getProcedureSha256() == null
                || !document.getProcedureSha256().equals(document.getProcedureValidatedSha256()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return com.yangaobo.servicedesk.api.knowledge.v1.ReviewedProcedure.newBuilder()
                .setSource(source).setDefinitionSha256(document.getProcedureSha256())
                .setDefinitionJson(com.google.protobuf.ByteString.copyFromUtf8(document.getProcedureDefinition().toString())).build();
    }

    /**
     * 只返回当前有权且适用的发布任务；超过预算明确失败。
     */
    public AuthorizedSources authorized(SourceQuery query) {
        if (query.hasRunId() != query.hasSourceUsageId())
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        boolean history = query.hasRunId();
        if (query.hasReferencedDocumentId() || query.hasReferencedIndexJobId())
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        if (history) {
            query = ai.resolve(query);
            if (!query.hasReferencedDocumentId() || !query.hasReferencedIndexJobId())
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        VerifySessionForm ref = ref(query.getActor());
        var actor = identities.verifySession(ref);
        if (actor.getMustChangePassword()) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        boolean staff =
                actor.getRoles().contains("TECHNICIAN")
                        || actor.getRoles().contains("SERVICE_AGENT");
        if (!staff && !actor.getRoles().contains("CUSTOMER_USER"))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        SupportAccessForm form = new SupportAccessForm();
        form.setOwnerId(ref.getOwnerId());
        form.setSessionRef(ref.getSessionRef());
        form.setAuthVersion(ref.getAuthVersion());
        form.setPurpose(query.getPurpose());
        form.setServiceItemId(query.hasServiceItemId() ? query.getServiceItemId() : null);
        form.setDeviceId(query.hasDeviceId() ? query.getDeviceId() : null);
        form.setTicketId(query.hasTicketId() ? query.getTicketId() : null);
        var environment = new SupportEnvironment();
        var wire = query.getEnvironment();
        environment.setOsName(wire.hasOsName() ? wire.getOsName() : null);
        environment.setOsVersion(wire.hasOsVersion() ? wire.getOsVersion() : null);
        environment.setSoftwareName(wire.hasSoftwareName() ? wire.getSoftwareName() : null);
        environment.setSoftwareVersion(
                wire.hasSoftwareVersion() ? wire.getSoftwareVersion() : null);
        environment.setErrorCode(wire.hasErrorCode() ? wire.getErrorCode() : null);
        environment.setConnectionType(wire.getConnectionType());
        environment.setAffectedScope(wire.getAffectedScope());
        form.setEnvironment(environment);
        var context = support.check(form);
        if (context.getServiceItemId() == null) return AuthorizedSources.getDefaultInstance();
        var matching = rules.scanForService(context.getServiceItemId(), 25001);
        if (matching.size() > 25000)
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        var allowed = new TreeSet<String>();
        for (var rule : matching)
            if (KnowledgeRules.matches(rule, context)) allowed.add(rule.getDocumentId());
        if (allowed.size() > 500) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        var result = AuthorizedSources.newBuilder();
        for (String id : allowed) {
            if (query.hasReferencedDocumentId() && !id.equals(query.getReferencedDocumentId()))
                continue;
            var document = documents.selectById(id);
            if (document == null || !"ALLOWED".equals(document.getAccessStatus())) continue;
            var parent = series.selectById(document.getSeriesId());
            if (parent == null || (!staff && !"CUSTOMER".equals(parent.getAudience()))) continue;
            // 仅实际历史引用且当前范围、受众和访问权均满足时说明风险原因。
            if (history && "WITHDRAWN".equals(document.getPublicationStatus()))
                throw new BusinessException(EvidenceErrorCode.SOURCE_WITHDRAWN);
            if (!KnowledgeRules.readable(document, history)) continue;
            var job = jobs.selectById(document.getCurrentJobId());
            if (job == null
                    || !IndexTransactionStateEnum.COMMITTED.getValue().equals(job.getSubmitState())
                    || !Set.of(
                            IndexTransactionStateEnum.NONE.getValue(),
                            IndexTransactionStateEnum.ABORTED.getValue())
                    .contains(job.getCancelState())) continue;
            if (query.hasReferencedIndexJobId()
                    && !job.getId().equals(query.getReferencedIndexJobId())) continue;
            var source = KnowledgeSource.newBuilder()
                    .setDocumentId(document.getId())
                    .setIndexJobId(job.getId())
                    .setRowVersion(document.getRowVersion())
                    .setTitle(document.getTitle())
                    .setContentFormat(document.getContentFormat())
                    .setSourceType(parent.getSourceType())
                    .setAudience(parent.getAudience());
            if (document.getProcedureSha256() != null
                    && document.getProcedureSha256().equals(document.getProcedureValidatedSha256())) {
                source.setProcedureSha256(document.getProcedureSha256());
                for (var check : document.getProcedureDefinition().path("checks"))
                    source.addProcedureCheckKeys(check.path("checkKey").asText());
            }
            result.addSources(source);
        }
        return result.build();
    }

    /**
     * 交付前核验全部知识来源，包括进入上下文但未最终引用的片段。
     */
    public AuthorizedSources verify(SourceQuery query, List<KnowledgeSource> sources) {
        if (sources.size() > 64) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        var current = authorized(query);
        var result = AuthorizedSources.newBuilder();
        for (var source : sources) {
            var match =
                    current.getSourcesList().stream()
                            .filter(
                                    value ->
                                            value.getDocumentId().equals(source.getDocumentId())
                                                    && value.getIndexJobId()
                                                    .equals(source.getIndexJobId())
                                                    && (query.hasRunId() || value.getRowVersion()
                                                    == source.getRowVersion()))
                            .findFirst();
            if (match.isEmpty()) throw new BusinessException(CommonErrorCode.FORBIDDEN);
            result.addSources(match.get());
        }
        return result.build();
    }

    /**
     * 只核验会话引用，不从RPC请求继承角色或客户范围。
     */
    private VerifySessionForm ref(ActorRef actor) {
        var result = new VerifySessionForm();
        result.setOwnerId(actor.getOwnerId());
        result.setSessionRef(actor.getSessionRef());
        result.setAuthVersion(actor.getAuthVersion());
        return result;
    }
}
