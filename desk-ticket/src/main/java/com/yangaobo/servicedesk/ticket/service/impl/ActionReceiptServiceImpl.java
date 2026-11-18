package com.yangaobo.servicedesk.ticket.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.ticket.mapper.ActionReceiptMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.service.IActionReceiptService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 以数据库唯一键保护操作者动作及草稿根，回执与业务结果共享事务。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ActionReceiptServiceImpl implements IActionReceiptService {
    private final ActionReceiptMapper receiptMapper;
    private final TicketMapper ticketMapper;
    private final TicketEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public ActionReceiptEntity replay(IdentityDTO actor, String key, String hash) {
        ActionReceiptEntity value = receiptMapper.byAction(actor.getOwnerId(), key);
        if (value == null) return null;
        authorize(actor, value);
        if (!value.getRequestHash().equals(hash)) throw new BusinessException(CommonErrorCode.CONFLICT);
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActionReceiptEntity replayDraft(IdentityDTO actor, String root, String hash) {
        if (root == null) return null;
        ActionReceiptEntity value = receiptMapper.byDraft(root);
        if (value == null) return null;
        authorize(actor, value);
        if (!value.getRequestHash().equals(hash)) throw new BusinessException(CommonErrorCode.CONFLICT);
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO byAction(IdentityDTO actor, String key) {
        ActionReceiptEntity value = receiptMapper.byAction(actor.getOwnerId(), key);
        authorize(actor, value);
        return view(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO byDraft(IdentityDTO actor, String root) {
        ActionReceiptEntity value = receiptMapper.byDraft(root);
        authorize(actor, value);
        return view(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO reconcileDraft(String ownerId, String root) {
        ActionReceiptEntity value = receiptMapper.byDraft(root);
        if (value == null || !ownerId.equals(value.getActorId())) return null;
        return view(value);
    }

    /**
     * 原账号及当前工单资源权限均不可由旧回执替代。
     */
    private void authorize(IdentityDTO actor, ActionReceiptEntity value) {
        if (value == null || !actor.getOwnerId().equals(value.getActorId()))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        TicketEntity ticket = ticketMapper.selectById(value.getTicketId());
        if (ticket == null || !SupportAuthorization.canRead(actor, ticket))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ActionReceiptEntity record(IdentityDTO actor, TicketEntity ticket, OnsiteBookingEntity booking,
                                      TicketEventEntity event, String type, String key, String hash,
                                      String draftId, String draftRootId, LocalDateTime now) {
        return persist(actor, ticket, booking, null, event, type, key, hash, draftId, draftRootId, now);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ActionReceiptEntity recordAttachment(IdentityDTO actor, TicketEntity ticket, AttachmentEntity attachment,
                                                TicketEventEntity event, String type, String key, String hash, LocalDateTime now) {
        return persist(actor, ticket, null, attachment, event, type, key, hash, null, null, now);
    }

    /**
     * 统一写入不可变回执，所有正式资源快照在提交时固定。
     */
    private ActionReceiptEntity persist(IdentityDTO actor, TicketEntity ticket, OnsiteBookingEntity booking,
                                        AttachmentEntity attachment, TicketEventEntity event, String type, String key, String hash,
                                        String draftId, String draftRootId, LocalDateTime now) {
        ActionReceiptEntity value = new ActionReceiptEntity();
        value.setId(IdentifierGenerator.next());
        value.setActorId(actor.getOwnerId());
        value.setClientActionId(key);
        value.setActionType(type);
        value.setRequestHash(hash);
        value.setSourceDraftId(draftId);
        value.setSourceDraftRootId(draftRootId);
        value.setTicketId(ticket.getId());
        value.setBookingId(booking == null ? null : booking.getId());
        value.setResultCode(booking != null && "REJECTED".equals(booking.getStatus()) ? booking.getResultCode() : "APPLIED");
        var snapshot = objectMapper.createObjectNode().put("schema_version", 1)
                .put("ticket_id", ticket.getId()).put("ticket_row_version", ticket.getRowVersion())
                .put("ticket_status", ticket.getStatus()).put("event_sequence", ticket.getEventSequence());
        snapshot.put("event_id", event == null ? null : event.getId());
        snapshot.put("booking_id", booking == null ? null : booking.getId());
        snapshot.put("booking_status", booking == null ? null : booking.getStatus());
        snapshot.put("booking_row_version", booking == null ? null : booking.getRowVersion());
        if (attachment != null) {
            snapshot.put("attachment_id", attachment.getId()).put("attachment_status", attachment.getStatus())
                    .put("attachment_row_version", attachment.getRowVersion()).put("redaction_version", attachment.getRedactionVersion())
                    .put("ai_use_allowed", attachment.getAiUseAllowed());
        }
        value.setResultSnapshot(snapshot);
        value.setCreatedAt(now);
        receiptMapper.insert(value);
        if (event != null && eventMapper.bindReceipt(event.getId(), ticket.getId(), value.getId()) != 1)
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO view(ActionReceiptEntity value) {
        var snapshot = value.getResultSnapshot();
        TicketActionReceiptVO result = new TicketActionReceiptVO();
        result.setReceiptId(value.getId());
        result.setActionType(value.getActionType());
        result.setClientActionId(value.getClientActionId());
        result.setTicketId(value.getTicketId());
        result.setBookingId(value.getBookingId());
        result.setSourceDraftId(value.getSourceDraftId());
        result.setSourceDraftRootId(value.getSourceDraftRootId());
        result.setResultCode(value.getResultCode());
        result.setEventId(snapshot.path("event_id").asText(null));
        result.setSequenceNumber(snapshot.path("event_sequence").longValue());
        result.setToStatus(snapshot.path("ticket_status").asText());
        result.setTicketRowVersion(snapshot.path("ticket_row_version").longValue());
        result.setBookingStatus(snapshot.path("booking_status").asText(null));
        result.setBookingRowVersion(snapshot.path("booking_row_version").isNull() ? null : snapshot.path("booking_row_version").longValue());
        result.setAttachmentId(snapshot.path("attachment_id").asText(null));
        result.setAttachmentStatus(snapshot.path("attachment_status").asText(null));
        result.setAttachmentRowVersion(snapshot.has("attachment_row_version") ? snapshot.path("attachment_row_version").longValue() : null);
        result.setRedactionVersion(snapshot.has("redaction_version") ? snapshot.path("redaction_version").longValue() : null);
        result.setAiUseAllowed(snapshot.has("ai_use_allowed") ? snapshot.path("ai_use_allowed").booleanValue() : null);
        return result;
    }
}
