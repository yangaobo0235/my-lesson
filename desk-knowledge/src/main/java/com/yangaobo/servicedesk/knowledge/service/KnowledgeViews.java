package com.yangaobo.servicedesk.knowledge.service;

import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEventEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentSeriesEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.IndexJobEntity;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentEventVO;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentSeriesVO;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentVO;
import com.yangaobo.servicedesk.knowledge.model.vo.IndexJobVO;

import java.time.ZoneOffset;

/**
 * 知识资源的安全公开字段投影，不暴露内部存储地址。
 *
 * @author yangaobo
 */
public final class KnowledgeViews {
    /**
     * 禁止创建无状态投影实例。
     */
    private KnowledgeViews() {
    }

    /**
     * DocumentSeries的明确公开字段。
     */
    public static DocumentSeriesVO of(DocumentSeriesEntity value) {
        var result = new DocumentSeriesVO();
        result.setId(value.getId());
        result.setSourceType(value.getSourceType());
        result.setSourceNamespace(value.getSourceNamespace());
        result.setSourceCode(value.getSourceCode());
        result.setSourceOrganization(value.getSourceOrganization());
        result.setLanguageCode(value.getLanguageCode());
        result.setAudience(value.getAudience());
        result.setCreatedBy(value.getCreatedBy());
        result.setCreatedAt(
                value.getCreatedAt() == null
                        ? null
                        : value.getCreatedAt().toInstant(ZoneOffset.UTC));
        return result;
    }

    /**
     * Document的明确公开字段。
     */
    public static DocumentVO of(DocumentEntity value) {
        var result = new DocumentVO();
        result.setId(value.getId());
        result.setSeriesId(value.getSeriesId());
        result.setClientUploadId(value.getClientUploadId());
        result.setRevisionNumber(value.getRevisionNumber());
        result.setTitle(value.getTitle());
        result.setContentFormat(value.getContentFormat());
        result.setOriginalFileName(value.getOriginalFileName());
        result.setFileSizeBytes(value.getFileSizeBytes());
        result.setFileSha256(value.getFileSha256());
        result.setSourceUrl(value.getSourceUrl());
        result.setSourceReviewNote(value.getSourceReviewNote());
        result.setRevisionLabel(value.getRevisionLabel());
        result.setIssuedOn(value.getIssuedOn());
        result.setApplicabilityNote(value.getApplicabilityNote());
        result.setProcedureDefinition(value.getProcedureDefinition());
        result.setProcedureSha256(value.getProcedureSha256());
        result.setProcedureValidatedSha256(value.getProcedureValidatedSha256());
        result.setProcedureValidatedAt(value.getProcedureValidatedAt() == null ? null : value.getProcedureValidatedAt().toInstant(ZoneOffset.UTC));
        result.setProcedureValidatedBy(value.getProcedureValidatedBy());
        result.setPublicationStatus(value.getPublicationStatus());
        result.setAccessStatus(value.getAccessStatus());
        result.setAccessReason(value.getAccessReason());
        result.setWithdrawalReason(value.getWithdrawalReason());
        result.setCurrentJobId(value.getCurrentJobId());
        result.setReplacesDocumentId(value.getReplacesDocumentId());
        result.setPublishedAt(
                value.getPublishedAt() == null
                        ? null
                        : value.getPublishedAt().toInstant(ZoneOffset.UTC));
        result.setPublishedBy(value.getPublishedBy());
        result.setDeletedAt(
                value.getDeletedAt() == null
                        ? null
                        : value.getDeletedAt().toInstant(ZoneOffset.UTC));
        result.setCreatedBy(value.getCreatedBy());
        result.setCreatedAt(
                value.getCreatedAt() == null
                        ? null
                        : value.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                value.getUpdatedAt() == null
                        ? null
                        : value.getUpdatedAt().toInstant(ZoneOffset.UTC));
        result.setRowVersion(value.getRowVersion());
        return result;
    }

    /**
     * DocumentEvent的明确公开字段。
     */
    public static DocumentEventVO of(DocumentEventEntity value) {
        var result = new DocumentEventVO();
        result.setId(value.getId());
        result.setDocumentId(value.getDocumentId());
        result.setActorId(value.getActorId());
        result.setEventType(value.getEventType());
        result.setRelatedDocumentId(value.getRelatedDocumentId());
        result.setDetails(value.getDetails());
        result.setCreatedAt(
                value.getCreatedAt() == null
                        ? null
                        : value.getCreatedAt().toInstant(ZoneOffset.UTC));
        return result;
    }

    /**
     * IndexJob的明确公开字段。
     */
    public static IndexJobVO of(IndexJobEntity value) {
        var result = new IndexJobVO();
        result.setId(value.getId());
        result.setDocumentId(value.getDocumentId());
        result.setAttemptNumber(value.getAttemptNumber());
        result.setRequestedBy(value.getRequestedBy());
        result.setClientRequestId(value.getClientRequestId());
        result.setRequestHash(value.getRequestHash());
        result.setSubmitState(value.getSubmitState());
        result.setSubmitDeadlineAt(
                value.getSubmitDeadlineAt() == null
                        ? null
                        : value.getSubmitDeadlineAt().toInstant(ZoneOffset.UTC));
        result.setSubmitCommittedAt(
                value.getSubmitCommittedAt() == null
                        ? null
                        : value.getSubmitCommittedAt().toInstant(ZoneOffset.UTC));
        result.setCancelOperationId(value.getCancelOperationId());
        result.setCancelRequestedBy(value.getCancelRequestedBy());
        result.setCancelState(value.getCancelState());
        result.setCancelDeadlineAt(
                value.getCancelDeadlineAt() == null
                        ? null
                        : value.getCancelDeadlineAt().toInstant(ZoneOffset.UTC));
        result.setCancelCommittedAt(
                value.getCancelCommittedAt() == null
                        ? null
                        : value.getCancelCommittedAt().toInstant(ZoneOffset.UTC));
        result.setLastErrorCode(value.getLastErrorCode());
        result.setCreatedAt(
                value.getCreatedAt() == null
                        ? null
                        : value.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                value.getUpdatedAt() == null
                        ? null
                        : value.getUpdatedAt().toInstant(ZoneOffset.UTC));
        return result;
    }
}
