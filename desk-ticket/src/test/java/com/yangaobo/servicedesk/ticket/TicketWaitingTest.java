package com.yangaobo.servicedesk.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.ActionDraftClient;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.mapper.DeviceMapper;
import com.yangaobo.servicedesk.ticket.mapper.ActionReceiptMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import com.yangaobo.servicedesk.ticket.service.impl.ActionReceiptServiceImpl;
import com.yangaobo.servicedesk.ticket.mapper.ProductModelMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketInformationForm;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.ITicketEventService;
import com.yangaobo.servicedesk.ticket.service.impl.TicketServiceImpl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 等待用例状态和提醒行为回归；不以仓储替身证明真实数据库事务。
 *
 * @author yangaobo
 */
class TicketWaitingTest {
    /**
     * 固定数据库决策时刻。
     */
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 16, 8, 0);
    /**
     * 当前有权处理人。
     */
    private IdentityDTO actor;
    /**
     * 每个用例独立工单。
     */
    private TicketEntity ticket;
    /**
     * 真实服务用例。
     */
    private TicketServiceImpl service;
    /**
     * 工单持久化边界替身。
     */
    private TicketMapper tickets;
    /**
     * 原动作查询替身。
     */
    private TicketEventMapper events;
    /**
     * 时间线与通知边界替身。
     */
    private ITicketEventService timeline;
    /**
     * 统一原动作回执仓储替身。
     */
    private ActionReceiptMapper receipts;

    /**
     * 装配本用例输入和事务边界，无外部连接。
     */
    @BeforeEach
    void prepare() {
        tickets = mock(TicketMapper.class);
        events = mock(TicketEventMapper.class);
        timeline = mock(ITicketEventService.class);
        receipts = mock(ActionReceiptMapper.class);
        when(events.bindReceipt(anyString(), anyString(), anyString())).thenReturn(1);
        var transactions = mock(BusinessTransaction.class);
        when(transactions.execute(any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        var mapper = new ObjectMapper().findAndRegisterModules();
        service = new TicketServiceImpl(events, timeline, mock(IServiceEntitlementService.class),
                mock(DeviceMapper.class), mock(ProductModelMapper.class), new RequestDigest(mapper),
                transactions, mapper, mock(IdentityVerificationClient.class), mock(ActionDraftClient.class),
                new ActionReceiptServiceImpl(receipts, tickets, events, mapper));
        ReflectionTestUtils.setField(service, "baseMapper", tickets);
        actor = new IdentityDTO();
        actor.setMustChangePassword(false);
        actor.setOwnerId("a".repeat(32));
        actor.setRoles(Set.of("TECHNICIAN"));
        actor.setCustomerIds(Set.of("b".repeat(32)));
        ticket = new TicketEntity();
        ticket.setId("c".repeat(32));
        ticket.setCustomerId("b".repeat(32));
        ticket.setRequesterId("d".repeat(32));
        ticket.setAssigneeId(actor.getOwnerId());
        ticket.setStatus("PROCESSING");
        ticket.setOnsiteEligible(true);
        ticket.setWaitCycle(0);
        ticket.setRowVersion(3L);
        ticket.setDiagnosisContextVersion(7L);
        ticket.setEventSequence(10L);
        when(tickets.lock(ticket.getId())).thenReturn(ticket);
        when(tickets.selectById(ticket.getId())).thenReturn(ticket);
        when(tickets.decisionTime()).thenReturn(now);
        when(tickets.update(eq(ticket), any())).thenReturn(1);
        when(timeline.append(any(), any(), anyString(), any(), any(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(call -> {
                    var event = new TicketEventEntity();
                    event.setId("e".repeat(32));
                    event.setTicketId(ticket.getId());
                    event.setEventType(call.getArgument(2));
                    event.setToStatus(ticket.getStatus());
                    event.setSequenceNumber(ticket.getEventSequence() + 1);
                    return event;
                });
    }

    /**
     * 外部等待关闭现场资格，保持责任和环境版本。
     */
    @Test
    void externalWaitRetainsHandlerAndSchedulesFollowUp() {
        service.waitExternal(actor, ticket.getId(), form());
        assertEquals("WAITING_EXTERNAL", ticket.getStatus());
        assertFalse(ticket.getOnsiteEligible());
        assertEquals(actor.getOwnerId(), ticket.getAssigneeId());
        assertEquals(7L, ticket.getDiagnosisContextVersion());
        assertEquals(1, ticket.getWaitCycle());
        assertEquals(now.plusHours(2), ticket.getFollowUpAt());
        assertEquals("等待客户网络管理员核对策略", ticket.getWaitReason());
    }

    /**
     * 活动预约必须人工先处理，不在等待动作里静默撤销。
     */
    @Test
    void activeBookingPreventsExternalWait() {
        when(tickets.activeBookings(ticket.getId())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.waitExternal(actor, ticket.getId(), form()));
        verify(tickets, never()).update(any(TicketEntity.class), any());
        verifyNoInteractions(timeline);
    }

    /**
     * 期限恰好等于数据库时刻也不能新建等待。
     */
    @Test
    void elapsedDeadlineDoesNotWrite() {
        var input = form();
        input.setFollowUpAt(now.toInstant(ZoneOffset.UTC));
        assertThrows(BusinessException.class, () -> service.requestInformation(actor, ticket.getId(), input));
        verify(tickets, never()).update(any(TicketEntity.class), any());
        verifyNoInteractions(timeline);
    }

    /**
     * 未分派给当前操作者的有范围技术人员不能控制等待。
     */
    @Test
    void otherTechnicianCannotStartWait() {
        actor.setOwnerId("f".repeat(32));
        assertThrows(BusinessException.class, () -> service.waitExternal(actor, ticket.getId(), form()));
        verifyNoInteractions(timeline);
    }

    /**
     * 跟进结束等待但不清除历史轮次，不把跟进解释为修复。
     */
    @Test
    void endWaitReturnsToProcessingAndClearsOnlyCurrentDeadline() {
        service.requestInformation(actor, ticket.getId(), form());
        TicketContentForm input = new TicketContentForm();
        input.setClientActionId("9".repeat(32));
        input.setRowVersion(ticket.getRowVersion());
        input.setContent("已取得所需材料，继续处理");
        service.endWait(actor, ticket.getId(), input);
        assertEquals("PROCESSING", ticket.getStatus());
        assertNull(ticket.getWaitReason());
        assertNull(ticket.getFollowUpAt());
        assertNull(ticket.getFollowUpNotifiedAt());
        assertEquals(1, ticket.getWaitCycle());
        assertNull(ticket.getResolutionType());
    }

    /**
     * 重复扫描不重复提醒，续期后新轮次允许下一次提醒。
     */
    @Test
    void scanNotifiesOncePerWaitCycle() {
        service.requestInformation(actor, ticket.getId(), form());
        when(tickets.scanDue(any(), anyInt())).thenReturn(List.of(ticket));
        when(tickets.decisionTime()).thenReturn(now.plusHours(3));
        assertEquals(1, service.scan(10));
        assertEquals(0, service.scan(10));
        assertEquals("WAITING_CUSTOMER", ticket.getStatus());
        var next = form();
        next.setRowVersion(ticket.getRowVersion());
        next.setFollowUpAt(now.plusHours(4).toInstant(ZoneOffset.UTC));
        service.followUp(actor, ticket.getId(), next);
        assertNull(ticket.getFollowUpNotifiedAt());
        assertEquals(2, ticket.getWaitCycle());
        when(tickets.decisionTime()).thenReturn(now.plusHours(5));
        assertEquals(1, service.scan(10));
        verify(timeline, times(2)).append(eq(ticket), isNull(), eq("FOLLOW_UP_DUE"), isNull(), isNull(),
                eq("WAITING_CUSTOMER"), eq("INTERNAL"), anyString(), any(ObjectNode.class), any(), isNull());
    }

    /**
     * 旧扫描候选在用户补充后不得产生提醒。
     */
    @Test
    void changedStateInvalidatesScanCandidate() {
        when(tickets.scanDue(any(), anyInt())).thenReturn(List.of(ticket));
        assertEquals(0, service.scan(10));
        verifyNoInteractions(timeline);
    }

    /**
     * 原动作回执先于新版本和新状态判断，响应丢失不会多建等待轮次。
     */
    @Test
    void replayReturnsOriginalEventWithoutAnotherCycle() {
        var input = form();
        var original = new ActionReceiptEntity();
        original.setId("f".repeat(32));
        original.setActorId(actor.getOwnerId());
        original.setTicketId(ticket.getId());
        original.setRequestHash(new RequestDigest(new ObjectMapper().findAndRegisterModules())
                .actionHash("WAIT_EXTERNAL", ticket.getId(), input));
        original.setResultSnapshot(new ObjectMapper().createObjectNode().put("event_id", "e".repeat(32))
                .put("ticket_status", "WAITING_EXTERNAL").put("ticket_row_version", 4).put("event_sequence", 12)
                .putNull("booking_row_version"));
        when(receipts.byAction(actor.getOwnerId(), input.getClientActionId())).thenReturn(original);
        ticket.setStatus("CLOSED");
        ticket.setRowVersion(100L);
        assertEquals("e".repeat(32), service.waitExternal(actor, ticket.getId(), input).getEventId());
        assertEquals(0, ticket.getWaitCycle());
        verifyNoInteractions(timeline);
    }

    /**
     * 构造明确期限和原键的等待输入。
     */
    private TicketInformationForm form() {
        var result = new TicketInformationForm();
        result.setClientActionId("8".repeat(32));
        result.setRowVersion(3L);
        result.setContent("等待客户网络管理员核对策略");
        result.setFollowUpAt(now.plusHours(2).toInstant(ZoneOffset.UTC));
        return result;
    }
}
