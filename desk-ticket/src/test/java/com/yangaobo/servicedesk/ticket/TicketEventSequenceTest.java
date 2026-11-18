package com.yangaobo.servicedesk.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.mapper.NotificationMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.service.impl.TicketEventServiceImpl;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 时间线分配失败不得写事件；序号独立于诊断环境和工单写版本。
 *
 * @author yangaobo
 */
class TicketEventSequenceTest {
    /**
     * 高于32位范围的事件仍按long递增，不改环境和写版本。
     */
    @Test
    void allocatesLongSequenceWithoutChangingOtherVersions() {
        var ticket = ticket();
        var tickets = mock(TicketMapper.class);
        var events = mock(TicketEventMapper.class);
        var notifications = mock(NotificationMapper.class);
        when(tickets.advanceEventSequence(ticket.getId(), ticket.getEventSequence())).thenReturn(1);
        var service = new TicketEventServiceImpl(events, notifications, tickets);
        var event = service.append(ticket, null, "ACCEPTANCE_OVERDUE", null, null, "SUBMITTED",
                "INTERNAL", "首次受理超期", new ObjectMapper().createObjectNode(), LocalDateTime.now(), null);
        assertEquals(2147483649L, event.getSequenceNumber());
        assertEquals(event.getSequenceNumber(), ticket.getEventSequence());
        assertEquals(2L, ticket.getDiagnosisContextVersion());
        assertEquals(9L, ticket.getRowVersion());
        verifyNoInteractions(notifications);
    }

    /**
     * 旧对象的事件游标不能覆盖已提交的下一序号。
     */
    @Test
    void staleSequenceFailsBeforeWritingEventOrNotification() {
        var ticket = ticket();
        var tickets = mock(TicketMapper.class);
        var events = mock(TicketEventMapper.class);
        var notifications = mock(NotificationMapper.class);
        var service = new TicketEventServiceImpl(events, notifications, tickets);
        var failure = assertThrows(BusinessException.class, () -> service.append(ticket, null,
                "ACCEPTANCE_OVERDUE", null, null, "SUBMITTED", "INTERNAL", "首次受理超期",
                new ObjectMapper().createObjectNode(), LocalDateTime.now(), null));
        assertEquals(CommonErrorCode.CONFLICT, failure.getErrorCode());
        assertEquals(2147483648L, ticket.getEventSequence());
        verifyNoInteractions(events, notifications);
    }

    /**
     * 构造三个版本明确不同的工单。
     */
    private TicketEntity ticket() {
        var ticket = new TicketEntity();
        ticket.setId("a".repeat(32));
        ticket.setStatus("SUBMITTED");
        ticket.setEventSequence(2147483648L);
        ticket.setDiagnosisContextVersion(2L);
        ticket.setRowVersion(9L);
        return ticket;
    }
}
