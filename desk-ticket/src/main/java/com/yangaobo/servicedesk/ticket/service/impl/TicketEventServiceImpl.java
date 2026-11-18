package com.yangaobo.servicedesk.ticket.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.mapper.NotificationMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.NotificationEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.enums.VisibilityEnum;
import com.yangaobo.servicedesk.ticket.service.ITicketEventService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在调用者工单事务内统一追加时间线与收件人通知。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketEventServiceImpl implements ITicketEventService {
    private final TicketEventMapper eventMapper;
    private final NotificationMapper notificationMapper;
    private final TicketMapper ticketMapper;

    /**
     * 调用者须已锁定工单；此方法不自行开启或提交事务。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public TicketEventEntity append(
            TicketEntity ticket,
            IdentityDTO actor,
            String type,
            String key,
            String hash,
            String from,
            String visibility,
            String content,
            ObjectNode metadata,
            LocalDateTime now,
            String bookingId) {
        TicketEventEntity event = new TicketEventEntity();
        event.setId(IdentifierGenerator.next());
        event.setOnsiteBookingId(bookingId);
        event.setSourceDraftId(metadata.path("source_draft_id").asText(null));
        event.setTicketId(ticket.getId());
        long sequence = Math.addExact(ticket.getEventSequence(), 1L);
        if (ticketMapper.advanceEventSequence(ticket.getId(), ticket.getEventSequence()) != 1)
            throw new BusinessException(CommonErrorCode.CONFLICT);
        ticket.setEventSequence(sequence);
        event.setSequenceNumber(sequence);
        event.setEventType(type);
        event.setActorType(actor == null ? "SYSTEM" : "USER");
        event.setActorId(actor == null ? null : actor.getOwnerId());
        event.setClientActionId(key);
        event.setActionRequestHash(hash);
        event.setVisibility(visibility);
        event.setFromStatus(from);
        event.setToStatus(ticket.getStatus());
        event.setContent(content);
        metadata.put("schema_version", 1);
        event.setMetadata(metadata);
        event.setCreatedAt(now);
        eventMapper.insert(event);
        Set<String> recipients = new HashSet<>();
        if (VisibilityEnum.PUBLIC.getValue().equals(visibility))
            recipients.add(ticket.getRequesterId());
        if (ticket.getAssigneeId() != null) recipients.add(ticket.getAssigneeId());
        if (actor != null) recipients.remove(actor.getOwnerId());
        for (String recipient : recipients) {
            NotificationEntity notification = new NotificationEntity();
            notification.setId(IdentifierGenerator.next());
            notification.setTicketId(ticket.getId());
            notification.setEventId(event.getId());
            notification.setRecipientId(recipient);
            notification.setCreatedAt(now);
            notificationMapper.insert(notification);
        }
        return event;
    }
}
