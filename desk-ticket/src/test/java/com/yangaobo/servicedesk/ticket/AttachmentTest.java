package com.yangaobo.servicedesk.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.AttachmentStorage;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.DiagnosticText;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.mapper.ActionReceiptMapper;
import com.yangaobo.servicedesk.ticket.mapper.AttachmentMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentRedactionForm;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentUploadForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.service.ITicketEventService;
import com.yangaobo.servicedesk.ticket.service.impl.ActionReceiptServiceImpl;
import com.yangaobo.servicedesk.ticket.service.impl.AttachmentServiceImpl;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 附件完整用例的权限、未知结果及来源版本回归；仓储替身不代表数据库事务证据。
 *
 * @author yangaobo
 */
class AttachmentTest {
    private ValidatorFactory validation;
    private AttachmentServiceImpl service;
    private AttachmentStorage storage;
    private AttachmentMapper attachments;
    private IdentityDTO actor;
    private TicketEntity ticket;
    private final Map<String, AttachmentEntity> rows = new HashMap<>();
    private final Map<String, ActionReceiptEntity> receipts = new HashMap<>();

    /**
     * 装配真实授权与回执用例，外部存储和事务边界使用可控替身。
     */
    @BeforeEach
    void setup() {
        validation = Validation.buildDefaultValidatorFactory();
        var json = new ObjectMapper().findAndRegisterModules();
        var tickets = mock(TicketMapper.class);
        attachments = mock(AttachmentMapper.class);
        storage = mock(AttachmentStorage.class);
        var transactions = mock(BusinessTransaction.class);
        when(transactions.execute(any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        var receiptMapper = mock(ActionReceiptMapper.class);
        var eventMapper = mock(TicketEventMapper.class);
        when(eventMapper.bindReceipt(anyString(), anyString(), anyString())).thenReturn(1);
        when(receiptMapper.byAction(anyString(), anyString())).thenAnswer(call -> receipts.get(call.getArgument(1)));
        when(receiptMapper.insert(any(ActionReceiptEntity.class))).thenAnswer(call -> {
            ActionReceiptEntity value = call.getArgument(0);
            receipts.put(value.getClientActionId(), value);
            return 1;
        });
        var events = mock(ITicketEventService.class);
        when(events.append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            TicketEventEntity event = new TicketEventEntity();
            event.setId("f".repeat(32));
            ticket.setEventSequence(ticket.getEventSequence() + 1);
            return event;
        });
        service = new AttachmentServiceImpl(tickets, storage, transactions,
                new ActionReceiptServiceImpl(receiptMapper, tickets, eventMapper, json), events, json,
                new RequestDigest(json), validation.getValidator());
        ReflectionTestUtils.setField(service, "baseMapper", attachments);
        actor = new IdentityDTO();
        actor.setOwnerId("a".repeat(32));
        actor.setOrganizationId("b".repeat(32));
        actor.setRoles(Set.of("CUSTOMER_USER"));
        actor.setCustomerIds(Set.of());
        actor.setMustChangePassword(false);
        ticket = new TicketEntity();
        ticket.setId("c".repeat(32));
        ticket.setRequesterId(actor.getOwnerId());
        ticket.setCustomerId(actor.getOrganizationId());
        ticket.setStatus("PROCESSING");
        ticket.setRowVersion(4L);
        ticket.setEventSequence(3L);
        ticket.setDiagnosisContextVersion(2L);
        when(tickets.lock(ticket.getId())).thenReturn(ticket);
        when(tickets.selectById(ticket.getId())).thenReturn(ticket);
        when(tickets.decisionTime()).thenReturn(LocalDateTime.of(2026, 11, 16, 10, 0));
        when(attachments.insert(any(AttachmentEntity.class))).thenAnswer(call -> {
            AttachmentEntity value = call.getArgument(0);
            rows.put(value.getId(), value);
            return 1;
        });
        when(attachments.byRequest(anyString(), anyString())).thenAnswer(call -> rows.values().stream()
                .filter(row -> row.getUploadedBy().equals(call.getArgument(0)) && row.getClientUploadId().equals(call.getArgument(1))).findFirst().orElse(null));
        when(attachments.selectById(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(attachments.lock(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(attachments.saveState(any(), anyLong())).thenReturn(1);
        when(storage.bucket()).thenReturn("ticket-private");
        when(storage.key(anyString())).thenAnswer(call -> "ticket/ci/attachments/" + call.getArgument(0));
        when(storage.put(any(), any())).thenReturn("version-1");
        when(storage.read(any())).thenReturn(bytes());
    }

    /**
     * 释放校验器工厂资源。
     */
    @AfterEach
    void close() {
        validation.close();
    }

    /**
     * UTF-8、JSON 深度及尾随载荷都在外写前拒绝。
     */
    @Test
    void rejectsInvalidTextAndNestedOrTrailingJson() {
        assertThrows(BusinessException.class, () -> DiagnosticText.validate(new byte[]{(byte) 0xc3}, "TEXT"));
        assertThrows(BusinessException.class, () -> DiagnosticText.encode("\ud800"));
        assertThrows(BusinessException.class, () -> DiagnosticText.validate("[] {}".getBytes(StandardCharsets.UTF_8), "JSON"));
        assertThrows(BusinessException.class, () -> DiagnosticText.validate(("[".repeat(33) + "0" + "]".repeat(33)).getBytes(StandardCharsets.UTF_8), "JSON"));
        assertEquals("{\"ok\":true}", DiagnosticText.validate("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), "JSON"));
    }

    /**
     * 已就绪不等于 AI 可用；同键只返回原意图。
     */
    @Test
    void uploadOnceAndRejectChangedPayload() {
        var first = service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes());
        assertEquals("READY", first.getStatus());
        assertFalse(first.getAiUseAllowed());
        var second = service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes());
        assertEquals(first.getId(), second.getId());
        verify(storage, times(1)).put(any(), any());
        assertThrows(BusinessException.class, () -> service.upload(actor, ticket.getId(), upload(), "diagnostic.log", "changed".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 附件发现也必须先核工单当前范围，客户查询明确限制PUBLIC，且不访问原件。
     */
    @Test
    void discoveryEnforcesCurrentTicketScope() {
        when(attachments.listRedacted(ticket.getId(), true, null)).thenReturn(java.util.List.of());
        assertTrue(service.listRedacted(actor, ticket.getId(), null).isEmpty());
        verify(attachments).listRedacted(ticket.getId(), true, null);
        clearInvocations(attachments);
        actor.setOrganizationId("f".repeat(32));
        assertThrows(BusinessException.class, () -> service.listRedacted(actor, ticket.getId(), null));
        verifyNoInteractions(attachments, storage);
    }

    /**
     * 上传超时保留意图，查原键只能 HEAD/GET，不重新 PUT。
     */
    @Test
    void unknownIsReconciledWithoutRepeatingWrite() {
        when(storage.put(any(), any())).thenThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));
        var unknown = service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes());
        assertEquals("UNKNOWN", unknown.getStatus());
        when(storage.locate(any())).thenReturn("version-1");
        assertEquals("READY", service.byRequest(actor, upload().getClientUploadId()).getStatus());
        verify(storage, times(1)).put(any(), any());
    }

    /**
     * 删除可赢过在途 PUT，迟到的成功结果不能复活附件。
     */
    @Test
    void deletionWinsLateUpload() {
        when(storage.put(any(), any())).thenAnswer(call -> {
            AttachmentEntity value = call.getArgument(0);
            value.setStatus("DELETED");
            value.setDeletedAt(LocalDateTime.of(2026, 11, 16, 10, 0));
            return "late-version";
        });
        assertEquals("DELETED", service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes()).getStatus());
    }

    /**
     * 客户的内部附件及越组织访问均不能触发外部读取。
     */
    @Test
    void enforcesVisibilityAndCurrentCustomer() {
        var invalid = upload();
        invalid.setVisibility("INTERNAL");
        assertThrows(BusinessException.class, () -> service.upload(actor, ticket.getId(), invalid, "diagnostic.log", bytes()));
        var value = service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes());
        clearInvocations(storage);
        actor.setOrganizationId("e".repeat(32));
        assertThrows(BusinessException.class, () -> service.original(actor, ticket.getId(), value.getId()));
        assertThrows(BusinessException.class, () -> service.byRequest(actor, upload().getClientUploadId()));
        verifyNoInteractions(storage);
    }

    /**
     * 确认新脱敏版本及撤销都进入同一回执，重放不再推进版本。
     */
    @Test
    void confirmationAndDeletionPreserveOriginalReceipts() {
        var value = service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes());
        var form = new AttachmentRedactionForm();
        form.setClientActionId("1".repeat(32));
        form.setRowVersion(value.getRowVersion());
        form.setFileSha256(value.getFileSha256());
        form.setRedactedText("连接失败，地址已移除");
        form.setAllowAiUse(true);
        var confirmed = service.confirmRedaction(actor, ticket.getId(), value.getId(), form);
        assertEquals(1L, confirmed.getRedactionVersion());
        assertTrue(confirmed.getAiUseAllowed());
        var redacted = service.readRedacted(actor, ticket.getId(), value.getId(), 1);
        assertArrayEquals(DiagnosticText.encode(form.getRedactedText()), redacted.getBytes());
        assertEquals(DiagnosticText.sha256(redacted.getBytes()), redacted.getSha256());
        assertThrows(BusinessException.class, () -> service.readRedacted(actor, ticket.getId(), value.getId(), 2));
        assertEquals(4L, ticket.getRowVersion());
        assertEquals(2L, ticket.getDiagnosisContextVersion());
        var deletion = new TicketActionForm();
        deletion.setClientActionId("2".repeat(32));
        deletion.setRowVersion(confirmed.getAttachmentRowVersion());
        var deleted = service.deleteAttachment(actor, ticket.getId(), value.getId(), deletion);
        assertEquals("DELETED", deleted.getAttachmentStatus());
        assertFalse(deleted.getAiUseAllowed());
        assertEquals(confirmed, service.confirmRedaction(actor, ticket.getId(), value.getId(), form));
        assertThrows(BusinessException.class, () -> service.original(actor, ticket.getId(), value.getId()));
        assertThrows(BusinessException.class, () -> service.readRedacted(actor, ticket.getId(), value.getId(), 1));
        verify(storage, never()).locate(any());
    }

    /**
     * 处理人员不能代替原上传者授权正文给 AI。
     */
    @Test
    void anotherTechnicianCannotConfirmRedaction() {
        var value = service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes());
        actor.setOwnerId("e".repeat(32));
        actor.setRoles(Set.of("TECHNICIAN"));
        actor.setCustomerIds(Set.of(ticket.getCustomerId()));
        ticket.setAssigneeId(actor.getOwnerId());
        var form = new AttachmentRedactionForm();
        form.setClientActionId("1".repeat(32));
        form.setRowVersion(value.getRowVersion());
        form.setFileSha256(value.getFileSha256());
        form.setRedactedText("脱敏文本");
        form.setAllowAiUse(true);
        assertThrows(BusinessException.class, () -> service.confirmRedaction(actor, ticket.getId(), value.getId(), form));
        assertEquals(0L, rows.get(value.getId()).getRedactionVersion());
    }

    /**
     * 原始固定文本夹具。
     */
    private byte[] bytes() {
        return "连接超时".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * READY与已脱敏都不能替代明确AI授权，摘要异常也不能交付正文。
     */
    @Test
    void redactedReadRequiresConsentAndIntegrity() {
        var value = service.upload(actor, ticket.getId(), upload(), "diagnostic.log", bytes());
        clearInvocations(storage);
        assertThrows(BusinessException.class, () -> service.readRedacted(actor, ticket.getId(), value.getId(), 1));
        var form = new AttachmentRedactionForm();
        form.setClientActionId("1".repeat(32));
        form.setRowVersion(value.getRowVersion());
        form.setFileSha256(value.getFileSha256());
        form.setRedactedText("已脱敏的错误码");
        form.setAllowAiUse(false);
        var denied = service.confirmRedaction(actor, ticket.getId(), value.getId(), form);
        assertThrows(BusinessException.class, () -> service.readRedacted(actor, ticket.getId(), value.getId(), 1));
        form.setClientActionId("2".repeat(32));
        form.setRowVersion(denied.getAttachmentRowVersion());
        form.setAllowAiUse(true);
        service.confirmRedaction(actor, ticket.getId(), value.getId(), form);
        assertEquals("已脱敏的错误码", new String(service.readRedacted(actor, ticket.getId(), value.getId(), 2).getBytes(), StandardCharsets.UTF_8));
        rows.get(value.getId()).setRedactedText("被错误覆盖的正文");
        var failure = assertThrows(BusinessException.class, () -> service.readRedacted(actor, ticket.getId(), value.getId(), 2));
        assertEquals(CommonErrorCode.INTERNAL_ERROR, failure.getErrorCode());
        verifyNoInteractions(storage);
    }

    /**
     * 固定同一次上传输入。
     */
    private AttachmentUploadForm upload() {
        var form = new AttachmentUploadForm();
        form.setClientUploadId("d".repeat(32));
        form.setContentFormat("LOG");
        form.setVisibility("PUBLIC");
        return form;
    }
}
