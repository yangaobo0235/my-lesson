package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;
import com.yangaobo.servicedesk.ticket.model.vo.attachment.AttachmentVO;

/**
 * 只投影有权公开的附件字段，不把持久化对象直接序列化。
 *
 * @author yangaobo
 */
public final class AttachmentViews {
    /**
     * 禁止实例化无状态投影。
     */
    private AttachmentViews() {
    }

    /**
     * 由调用者先核验当前工单及附件可见性。
     */
    public static AttachmentVO view(AttachmentEntity value) {
        AttachmentVO result = new AttachmentVO();
        result.setId(value.getId());
        result.setTicketId(value.getTicketId());
        result.setUploadedBy(value.getUploadedBy());
        result.setClientUploadId(value.getClientUploadId());
        result.setOriginalFileName(value.getOriginalFileName());
        result.setContentFormat(value.getContentFormat());
        result.setVisibility(value.getVisibility());
        result.setStatus(value.getStatus());
        result.setFileSizeBytes(value.getFileSizeBytes());
        result.setFileSha256(value.getFileSha256());
        result.setRedactionVersion(value.getRedactionVersion());
        result.setRedactedSha256(value.getRedactedSha256());
        result.setAiUseAllowed(value.getAiUseAllowed());
        result.setConfirmedBy(value.getConfirmedBy());
        result.setConfirmedAt(value.getConfirmedAt());
        result.setDeletedAt(value.getDeletedAt());
        result.setErrorCode(value.getErrorCode());
        result.setCreatedAt(value.getCreatedAt());
        result.setUpdatedAt(value.getUpdatedAt());
        result.setRowVersion(value.getRowVersion());
        return result;
    }
}
