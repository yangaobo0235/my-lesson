package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.ticket.component.AttachmentStorage;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.DiagnosticText;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.mapper.AttachmentMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;
import com.yangaobo.servicedesk.ticket.model.dto.attachment.RedactedAttachment;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.enums.AttachmentStatusEnum;
import com.yangaobo.servicedesk.ticket.model.enums.VisibilityEnum;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentRedactionForm;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentUploadForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.vo.attachment.AttachmentVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.service.AttachmentViews;
import com.yangaobo.servicedesk.ticket.service.IActionReceiptService;
import com.yangaobo.servicedesk.ticket.service.IAttachmentService;
import com.yangaobo.servicedesk.ticket.service.ITicketEventService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import jakarta.validation.Validator;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 以持久意图协调外部存储；文件操作不持数据库锁，迟到结果不能复活已删除附件。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentServiceImpl extends ServiceImpl<AttachmentMapper, AttachmentEntity> implements IAttachmentService {
    private final TicketMapper ticketMapper;
    private final AttachmentStorage storage;
    private final BusinessTransaction transactions;
    private final IActionReceiptService receipts;
    private final ITicketEventService events;
    private final ObjectMapper json;
    private final RequestDigest digest;
    private final Validator validator;

    /**
     * {@inheritDoc}
     */
    @Override
    public AttachmentVO upload(IdentityDTO actor, String ticketId, AttachmentUploadForm form, String name, byte[] bytes) {
        valid(form);
        id(ticketId);
        DiagnosticText.validate(bytes, form.getContentFormat());
        if (name == null || name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        if (customer(actor) && !VisibilityEnum.PUBLIC.getValue().equals(form.getVisibility()))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        String fileHash = DiagnosticText.sha256(bytes);
        var input = json.valueToTree(form);
        ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("fileSha256", fileHash).put("fileSizeBytes", bytes.length).put("originalFileName", name);
        String hash = digest.hash("UPLOAD_ATTACHMENT", ticketId, input);
        AtomicBoolean created = new AtomicBoolean(false);
        AttachmentEntity intent = transactions.execute(() -> {
            ticket(actor, ticketMapper.lock(ticketId));
            AttachmentEntity old = replayUpload(actor, form.getClientUploadId(), hash);
            if (old != null) return old;
            var now = ticketMapper.decisionTime();
            AttachmentEntity value = new AttachmentEntity();
            value.setId(IdentifierGenerator.next());
            value.setTicketId(ticketId);
            value.setUploadedBy(actor.getOwnerId());
            value.setClientUploadId(form.getClientUploadId());
            value.setRequestHash(hash);
            value.setOriginalFileName(name);
            value.setContentFormat(form.getContentFormat());
            value.setVisibility(form.getVisibility());
            value.setStatus(AttachmentStatusEnum.UPLOADING.getValue());
            value.setStorageBucket(storage.bucket());
            value.setStorageKey(storage.key(value.getId()));
            value.setFileSizeBytes((long) bytes.length);
            value.setFileSha256(fileHash);
            value.setRedactionVersion(0L);
            value.setAiUseAllowed(false);
            value.setRowVersion(0L);
            value.setCreatedAt(now);
            value.setUpdatedAt(now);
            baseMapper.insert(value);
            created.set(true);
            return value;
        }, () -> replayUpload(actor, form.getClientUploadId(), hash));
        if (!created.get()) return AttachmentViews.view(intent);
        try {
            intent.setStorageVersionId(storage.put(intent, bytes));
            storage.read(intent);
        } catch (BusinessException failure) {
            settle(intent, false);
            return AttachmentViews.view(readable(actor, ticketId, intent.getId(), true));
        }
        settle(intent, true);
        return AttachmentViews.view(readable(actor, ticketId, intent.getId(), true));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttachmentVO byRequest(IdentityDTO actor, String key) {
        id(key);
        AttachmentEntity value = baseMapper.byRequest(actor.getOwnerId(), key);
        if (value == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        value = readable(actor, value.getTicketId(), value.getId(), true);
        if (AttachmentStatusEnum.UPLOADING.getValue().equals(value.getStatus()) || AttachmentStatusEnum.UNKNOWN.getValue().equals(value.getStatus())) {
            String version = storage.locate(value);
            if (version != null) {
                value.setStorageVersionId(version);
                storage.read(value);
                settle(value, true);
                value = readable(actor, value.getTicketId(), value.getId(), true);
            }
        }
        return AttachmentViews.view(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<AttachmentVO> list(IdentityDTO actor, String ticketId, BasePageForm form) {
        id(ticketId);
        valid(form);
        ticket(actor, ticketMapper.selectById(ticketId));
        return PageVO.of(baseMapper.listReadable(ticketId, customer(actor), form.getPageSize(), form.offset()).stream()
                .map(AttachmentViews::view).toList(), baseMapper.countReadable(ticketId, customer(actor)), form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public java.util.List<AttachmentVO> listRedacted(IdentityDTO actor, String ticketId, String afterId) {
        id(ticketId);
        if (afterId != null) id(afterId);
        ticket(actor, ticketMapper.selectById(ticketId));
        return baseMapper.listRedacted(ticketId, customer(actor), afterId).stream()
                .map(AttachmentViews::view).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] original(IdentityDTO actor, String ticketId, String attachmentId) {
        AttachmentEntity value = readable(actor, ticketId, attachmentId, false);
        ready(value);
        byte[] bytes = storage.read(value);
        ready(readable(actor, ticketId, attachmentId, false));
        return bytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RedactedAttachment readRedacted(IdentityDTO actor, String ticketId, String attachmentId, long redactionVersion) {
        if (redactionVersion < 1) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        AttachmentEntity value = readable(actor, ticketId, attachmentId, false);
        ready(value);
        if (!Boolean.TRUE.equals(value.getAiUseAllowed()) || value.getRedactedText() == null
                || !Objects.equals(value.getRedactionVersion(), redactionVersion))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        byte[] bytes = DiagnosticText.encode(value.getRedactedText());
        DiagnosticText.validate(bytes, value.getContentFormat());
        if (!DiagnosticText.sha256(bytes).equals(value.getRedactedSha256()))
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        return new RedactedAttachment(redactionVersion, value.getContentFormat(), value.getRedactedSha256(), bytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO confirmRedaction(IdentityDTO actor, String ticketId, String attachmentId, AttachmentRedactionForm form) {
        valid(form);
        byte[] bytes = DiagnosticText.encode(form.getRedactedText());
        return change(actor, ticketId, attachmentId, form, false, bytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO deleteAttachment(IdentityDTO actor, String ticketId, String attachmentId, TicketActionForm form) {
        valid(form);
        return change(actor, ticketId, attachmentId, form, true, null);
    }

    /**
     * 工单→附件锁序串行化确认、删除及迟到上传结果。
     */
    private TicketActionReceiptVO change(IdentityDTO actor, String ticketId, String attachmentId, TicketActionForm form, boolean deleting, byte[] bytes) {
        id(ticketId);
        id(attachmentId);
        String type = deleting ? "DELETE_ATTACHMENT" : "CONFIRM_REDACTION";
        String hash = digest.actionHash(type, ticketId + ":" + attachmentId, form);
        return transactions.execute(() -> {
            TicketEntity ticket = ticket(actor, ticketMapper.lock(ticketId));
            var old = receipts.replay(actor, form.getClientActionId(), hash);
            if (old != null) return receipts.view(old);
            AttachmentEntity value = baseMapper.lock(attachmentId);
            authorize(actor, ticketId, value, false);
            boolean uploader = actor.getOwnerId().equals(value.getUploadedBy());
            boolean handler = actor.getRoles().contains("SERVICE_AGENT")
                    || (actor.getRoles().contains("TECHNICIAN") && actor.getOwnerId().equals(ticket.getAssigneeId()));
            if (!uploader && !(deleting && handler)) throw new BusinessException(CommonErrorCode.FORBIDDEN);
            if (!Objects.equals(form.getRowVersion(), value.getRowVersion()))
                throw new BusinessException(CommonErrorCode.CONFLICT);
            var now = ticketMapper.decisionTime();
            if (deleting) {
                value.setStatus(AttachmentStatusEnum.DELETED.getValue());
                value.setDeletedAt(now);
                value.setAiUseAllowed(false);
            } else {
                ready(value);
                AttachmentRedactionForm redaction = (AttachmentRedactionForm) form;
                if (!value.getFileSha256().equals(redaction.getFileSha256()))
                    throw new BusinessException(CommonErrorCode.CONFLICT);
                DiagnosticText.validate(bytes, value.getContentFormat());
                value.setRedactedText(redaction.getRedactedText());
                value.setRedactedSha256(DiagnosticText.sha256(bytes));
                value.setRedactionVersion(Math.addExact(value.getRedactionVersion(), 1));
                value.setAiUseAllowed(redaction.getAllowAiUse());
                value.setConfirmedBy(actor.getOwnerId());
                value.setConfirmedAt(now);
            }
            value.setUpdatedAt(now);
            saveProjection(value);
            var metadata = json.createObjectNode().put("attachment_id", value.getId())
                    .put("redaction_version", value.getRedactionVersion()).put("ai_use_allowed", value.getAiUseAllowed());
            var event = events.append(ticket, actor, deleting ? "ATTACHMENT_DELETED" : "ATTACHMENT_REDACTED",
                    form.getClientActionId(), hash, ticket.getStatus(), value.getVisibility(),
                    deleting ? "附件访问已撤销" : "附件脱敏版本已确认", metadata, now, null);
            return receipts.view(receipts.recordAttachment(actor, ticket, value, event, type, form.getClientActionId(), hash, now));
        }, () -> {
            var old = receipts.replay(actor, form.getClientActionId(), hash);
            return old == null ? null : receipts.view(old);
        });
    }

    /**
     * 外部结果只收敛原意图；删除及既有 READY 永不回退。
     */
    private void settle(AttachmentEntity observed, boolean verified) {
        transactions.execute(() -> {
            ticketMapper.lock(observed.getTicketId());
            AttachmentEntity current = baseMapper.lock(observed.getId());
            if (!AttachmentStatusEnum.UPLOADING.getValue().equals(current.getStatus())
                    && !AttachmentStatusEnum.UNKNOWN.getValue().equals(current.getStatus())) return current;
            current.setStatus(verified ? AttachmentStatusEnum.READY.getValue() : AttachmentStatusEnum.UNKNOWN.getValue());
            current.setStorageVersionId(verified ? observed.getStorageVersionId() : null);
            current.setErrorCode(verified ? null : "UPLOAD_RESULT_UNKNOWN");
            current.setUpdatedAt(ticketMapper.decisionTime());
            saveProjection(current);
            return current;
        }, () -> null);
    }

    /**
     * 更新显式比较原版本，不借通用非空更新遗漏 NULL。
     */
    private void saveProjection(AttachmentEntity value) {
        long expected = value.getRowVersion();
        value.setRowVersion(Math.addExact(expected, 1));
        if (baseMapper.saveState(value, expected) != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 原上传键语义不得改变，旧回执也必须重新授权。
     */
    private AttachmentEntity replayUpload(IdentityDTO actor, String key, String hash) {
        AttachmentEntity value = baseMapper.byRequest(actor.getOwnerId(), key);
        if (value == null) return null;
        readable(actor, value.getTicketId(), value.getId(), true);
        if (!hash.equals(value.getRequestHash())) throw new BusinessException(CommonErrorCode.CONFLICT);
        return value;
    }

    /**
     * 每次访问都核验资源归属及当前可见性，不以附件 ID 授权。
     */
    private AttachmentEntity readable(IdentityDTO actor, String ticketId, String attachmentId, boolean includeDeleted) {
        id(ticketId);
        id(attachmentId);
        ticket(actor, ticketMapper.selectById(ticketId));
        AttachmentEntity value = baseMapper.selectById(attachmentId);
        authorize(actor, ticketId, value, includeDeleted);
        return value;
    }

    /**
     * 客户不可通过附件元信息窥探内部记录。
     */
    private void authorize(IdentityDTO actor, String ticketId, AttachmentEntity value, boolean includeDeleted) {
        if (value == null || !ticketId.equals(value.getTicketId())
                || (customer(actor) && !VisibilityEnum.PUBLIC.getValue().equals(value.getVisibility()))
                || (!includeDeleted && AttachmentStatusEnum.DELETED.getValue().equals(value.getStatus())))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }

    /**
     * 当前工单身份和强制改密状态统一拒绝。
     */
    private TicketEntity ticket(IdentityDTO actor, TicketEntity value) {
        if (actor.getMustChangePassword() || value == null || !SupportAuthorization.canRead(actor, value))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return value;
    }

    /**
     * 客户角色优先适用较窄的附件权限。
     */
    private boolean customer(IdentityDTO actor) {
        return actor.getRoles().contains("CUSTOMER_USER");
    }

    /**
     * 只有已验证原件允许读正文及确认脱敏。
     */
    private void ready(AttachmentEntity value) {
        if (!AttachmentStatusEnum.READY.getValue().equals(value.getStatus()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 内部用例同样验证输入，不只依赖 Controller。
     */
    private void valid(Object value) {
        if (value == null || !validator.validate(value).isEmpty())
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 路径和内部标识执行相同格式校验。
     */
    private void id(String value) {
        if (value == null || !value.matches("[0-9a-f]{32}"))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
