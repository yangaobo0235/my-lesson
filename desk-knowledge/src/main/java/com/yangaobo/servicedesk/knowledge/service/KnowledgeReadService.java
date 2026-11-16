package com.yangaobo.servicedesk.knowledge.service;

import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.knowledge.v1.SourceQuery;
import com.yangaobo.servicedesk.api.ticket.v1.Environment;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.enums.PublicationStatusEnum;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeReadForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentVO;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 当前授权发布知识的数据库分页与原件读取授权。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeReadService {
    private final KnowledgeAccessService access;
    private final DocumentMapper documents;

    /**
     * 先取得当前有权修订集合，再在所属数据库分页。
     */
    public PageVO<DocumentVO> list(IdentityDTO actor, KnowledgeReadForm form) {
        var sources = access.authorized(query(actor, form));
        if (sources.getSourcesCount() == 0) {
            return PageVO.of(List.of(), 0, form);
        }
        var ids = sources.getSourcesList().stream().map(source -> source.getDocumentId()).toList();
        long total = documents.countAuthorized(ids);
        return PageVO.of(documents.listAuthorized(ids, form.getPageSize(), form.offset())
                .stream().map(KnowledgeViews::of).toList(), total, form);
    }

    /**
     * 原件和详情逐次核验当前发布、受众、适用条件和实际引用绑定。
     */
    public DocumentEntity readable(IdentityDTO actor, String id, KnowledgeReadForm form) {
        var sources = access.authorized(query(actor, form));
        if (sources.getSourcesList().stream()
                .noneMatch(source -> source.getDocumentId().equals(id)))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var document = documents.selectById(id);
        if (document == null
                || !PublicationStatusEnum.PUBLISHED
                .getValue()
                .equals(document.getPublicationStatus()))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return document;
    }

    /**
     * 仅知识管理员可预览固定原件版本，不因此取得私有工单对话。
     */
    public DocumentEntity manageable(IdentityDTO actor, String id) {
        KnowledgeRules.administrator(actor);
        var document = documents.selectById(id);
        if (document == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return document;
    }

    /**
     * 任何浏览器输入身份字段都不参与原身份引用。
     */
    private SourceQuery query(IdentityDTO actor, KnowledgeReadForm form) {
        var query =
                SourceQuery.newBuilder()
                        .setActor(
                                ActorRef.newBuilder()
                                        .setOwnerId(actor.getOwnerId())
                                        .setSessionRef(actor.getSessionRef())
                                        .setAuthVersion(actor.getAuthVersion()))
                        .setPurpose(form.getTicketId() == null ? "NEW_SELF_SERVICE" : "HISTORY");
        if (form.getServiceItemId() != null) query.setServiceItemId(form.getServiceItemId());
        if (form.getDeviceId() != null) query.setDeviceId(form.getDeviceId());
        if (form.getTicketId() != null) query.setTicketId(form.getTicketId());
        if ((form.getGenerationId() == null) != (form.getSourceUsageId() == null))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        if (form.getGenerationId() != null)
            query.setGenerationId(form.getGenerationId()).setSourceUsageId(form.getSourceUsageId());
        var environment =
                Environment.newBuilder().setConnectionType("UNKNOWN").setAffectedScope("UNKNOWN");
        var value = form.getEnvironment();
        if (value != null) {
            if (value.getOsName() != null) environment.setOsName(value.getOsName());
            if (value.getOsVersion() != null) environment.setOsVersion(value.getOsVersion());
            if (value.getSoftwareName() != null)
                environment.setSoftwareName(value.getSoftwareName());
            if (value.getSoftwareVersion() != null)
                environment.setSoftwareVersion(value.getSoftwareVersion());
            if (value.getErrorCode() != null) environment.setErrorCode(value.getErrorCode());
            if (value.getConnectionType() != null)
                environment.setConnectionType(value.getConnectionType());
            if (value.getAffectedScope() != null)
                environment.setAffectedScope(value.getAffectedScope());
        }
        return query.setEnvironment(environment).build();
    }
}
