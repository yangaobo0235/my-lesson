package com.yangaobo.servicedesk.ticket.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.NotificationMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.entity.NotificationEntity;
import com.yangaobo.servicedesk.ticket.model.enums.VisibilityEnum;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 在调用者工单事务内统一追加时间线与收件人通知。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Service
@lombok.RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketTimelineService {
    private final TicketEventMapper eventMapper;
    private final NotificationMapper notificationMapper;
    /** 调用者须已锁定工单；此方法不自行开启或提交事务。 */
    public TicketEventEntity append(TicketEntity ticket, IdentityDTO actor, String type, String key,
            String hash, String from, String visibility, String content, ObjectNode metadata,
            LocalDateTime now, String bookingId) {
        TicketEventEntity event = new TicketEventEntity();
        event.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
        event.setOnsiteBookingId(bookingId);
        event.setSourceDraftId(metadata.path("source_draft_id").asText(null));
        event.setTicketId(ticket.getId());
        event.setSequenceNumber(eventMapper.lastSequence(ticket.getId()) + 1);
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
        Set<String> recipients = new java.util.HashSet<>();
        if (VisibilityEnum.PUBLIC.getValue().equals(visibility))
            recipients.add(ticket.getRequesterId());
        if (ticket.getAssigneeId() != null) recipients.add(ticket.getAssigneeId());
        if (actor != null) recipients.remove(actor.getOwnerId());
        for (String recipient : recipients) {
            NotificationEntity notification = new NotificationEntity();
            notification.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
            notification.setTicketId(ticket.getId());
            notification.setEventId(event.getId());
            notification.setRecipientId(recipient);
            notification.setCreatedAt(now);
            notificationMapper.insert(notification);
        }
        return event;
    }
}
