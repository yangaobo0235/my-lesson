package com.yangaobo.servicedesk.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.ActionDraftClient;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.mapper.ActionReceiptMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.dto.support.DraftValidation;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.service.impl.ActionReceiptServiceImpl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 原结果、当前权限和草稿证明边界回归；真实事务另由独占MySQL验证。
 *
 * @author yangaobo
 */
class ActionReceiptTest {
    /**
     * JSON及规范摘要使用真实组件。
     */
    private final ObjectMapper json = new ObjectMapper();
    /**
     * 原回执仓储替身。
     */
    private ActionReceiptMapper receipts;
    /**
     * 当前工单权限事实。
     */
    private TicketEntity ticket;
    /**
     * 当前操作者。
     */
    private IdentityDTO actor;
    /**
     * 原提交不可变事实。
     */
    private ActionReceiptEntity receipt;
    /**
     * 被验证的真实用例。
     */
    private ActionReceiptServiceImpl service;

    /**
     * 装配发生后续变更的工单，保留原提交快照。
     */
    @BeforeEach
    void prepare() {
        receipts = mock(ActionReceiptMapper.class);
        var tickets = mock(TicketMapper.class);
        service = new ActionReceiptServiceImpl(receipts, tickets, mock(TicketEventMapper.class), json);
        actor = new IdentityDTO();
        actor.setOwnerId("a".repeat(32));
        actor.setRoles(Set.of("TECHNICIAN"));
        actor.setCustomerIds(Set.of("b".repeat(32)));
        actor.setMustChangePassword(false);
        ticket = new TicketEntity();
        ticket.setId("c".repeat(32));
        ticket.setCustomerId("b".repeat(32));
        ticket.setStatus("CLOSED");
        ticket.setRowVersion(99L);
        when(tickets.selectById(ticket.getId())).thenReturn(ticket);
        receipt = new ActionReceiptEntity();
        receipt.setId("d".repeat(32));
        receipt.setActorId(actor.getOwnerId());
        receipt.setTicketId(ticket.getId());
        receipt.setRequestHash("e".repeat(64));
        receipt.setResultSnapshot(json.createObjectNode()
                .put("ticket_status", "PROCESSING").put("ticket_row_version", 2)
                .put("event_sequence", 3).putNull("booking_row_version"));
        when(receipts.byAction(actor.getOwnerId(), "f".repeat(32))).thenReturn(receipt);
        when(receipts.byDraft("1".repeat(32))).thenReturn(receipt);
    }

    /**
     * 当前工单变化不能覆盖原回执版本与状态。
     */
    @Test
    void returnsOriginalSnapshotAfterLaterChanges() {
        var value = service.byAction(actor, "f".repeat(32));
        assertEquals("PROCESSING", value.getToStatus());
        assertEquals(2L, value.getTicketRowVersion());
        assertNull(value.getBookingRowVersion());
    }

    /**
     * 当前客户范围被收回后，旧动作键不能继续读取。
     */
    @Test
    void revokedScopeHidesReceipt() {
        actor.setCustomerIds(Set.of());
        assertThrows(BusinessException.class, () -> service.byAction(actor, "f".repeat(32)));
    }

    /**
     * 即使其他技术人员能读工单，也不能读原操作者的草稿采用。
     */
    @Test
    void anotherActorCannotReadDraftReceipt() {
        actor.setOwnerId("2".repeat(32));
        assertThrows(BusinessException.class, () -> service.byDraft(actor, "1".repeat(32)));
    }

    /**
     * 系统对账必须匹配根的原拥有者，账号会话过期不抹去已提交业务事实。
     */
    @Test
    void systemReconciliationMatchesOriginalOwner() {
        actor.setCustomerIds(Set.of());
        assertEquals(receipt.getId(), service.reconcileDraft(actor.getOwnerId(), "1".repeat(32)).getReceiptId());
        assertNull(service.reconcileDraft("2".repeat(32), "1".repeat(32)));
        assertNull(service.reconcileDraft(actor.getOwnerId(), "3".repeat(32)));
        verify(receipts, never()).insert(any(ActionReceiptEntity.class));
    }

    /**
     * 同根只接受同一最终语义，禁止另一修订重复采用。
     */
    @Test
    void rootReplayRequiresIdenticalMeaning() {
        assertSame(receipt, service.replayDraft(actor, "1".repeat(32), "e".repeat(64)));
        assertThrows(BusinessException.class, () -> service.replayDraft(actor, "1".repeat(32), "0".repeat(64)));
    }

    /**
     * 请求键及修订ID不改变相同内容语义，但版本和正文改变必须冲突。
     */
    @Test
    void actionDigestRetainsContentAndVersion() {
        var digest = new RequestDigest(json);
        var input = json.createObjectNode().put("clientActionId", "one").put("sourceDraftId", "first")
                .put("sourceDraftRootId", "root").put("contentSha256", "hash").put("rowVersion", "3")
                .put("content", "已核验");
        String original = digest.actionHash("ADD_RECORD", "ticket", input);
        input.put("clientActionId", "two").put("sourceDraftId", "second");
        assertEquals(original, digest.actionHash("ADD_RECORD", "ticket", input));
        input.put("rowVersion", "4");
        assertNotEquals(original, digest.actionHash("ADD_RECORD", "ticket", input));
        input.put("rowVersion", "3").put("content", "未经确认的另一结论");
        assertNotEquals(original, digest.actionHash("ADD_RECORD", "ticket", input));
    }

    /**
     * 截止恰好等于数据库时间时拒绝正式提交，纯人工动作无需证明。
     */
    @Test
    void expiredProofCannotCommit() {
        var client = new ActionDraftClient(json, "unused", "", "", "", "");
        var now = LocalDateTime.of(2026, 11, 16, 9, 0);
        var proof = new DraftValidation();
        proof.setValidUntil(now.toInstant(ZoneOffset.UTC));
        assertThrows(BusinessException.class, () -> client.requireValid(proof, now));
        assertDoesNotThrow(() -> client.requireValid(proof, now.minusSeconds(1)));
        assertDoesNotThrow(() -> client.requireValid(null, now));
    }
}
