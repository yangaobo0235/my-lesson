package com.yangaobo.servicedesk.ticket.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.enums.ClosureReasonEnum;
import com.yangaobo.servicedesk.ticket.model.enums.TicketStatusEnum;
import com.yangaobo.servicedesk.ticket.model.enums.VisibilityEnum;
import com.yangaobo.servicedesk.ticket.service.ITicketDeadlineService;
import com.yangaobo.servicedesk.ticket.service.ITicketEventService;
import com.yangaobo.servicedesk.ticket.service.TicketStateWriter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 负责首次受理超期、等待提醒与确认超时的工单期限处理。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketDeadlineServiceImpl implements ITicketDeadlineService {
    private final TicketMapper ticketMapper;
    private final ITicketEventService timelineService;
    private final BusinessTransaction transactions;
    private final ObjectMapper objectMapper;
    private final TicketStateWriter stateWriter;

    /**
     * {@inheritDoc} 每笔重核后写入，不将扫描候选当当前状态。
     */
    @Override
    public int scan(int limit) {
        if (limit < 1 || limit > 200) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        LocalDateTime time = ticketMapper.decisionTime();
        List<TicketEntity> candidates = ticketMapper.scanDue(time, limit);
        int count = 0;
        for (TicketEntity candidate : candidates) {
            boolean changed =
                    transactions.execute(
                            () -> {
                                TicketEntity ticket = ticketMapper.lock(candidate.getId());
                                LocalDateTime now = ticketMapper.decisionTime();
                                if (TicketStatusEnum.SUBMITTED.getValue().equals(ticket.getStatus())
                                        && recordAcceptanceOverdue(ticket, now)) {
                                    stateWriter.save(ticket, now);
                                    return true;
                                }
                                if (Set.of("WAITING_CUSTOMER", "WAITING_EXTERNAL").contains(ticket.getStatus())
                                        && ticket.getFollowUpNotifiedAt() == null
                                        && !now.isBefore(ticket.getFollowUpAt())) {
                                    ticket.setFollowUpNotifiedAt(now);
                                    stateWriter.save(ticket, now);
                                    append(ticket, "FOLLOW_UP_DUE", ticket.getStatus(),
                                            VisibilityEnum.INTERNAL.getValue(), "已到本轮跟进期限，请处理人核对进展",
                                            objectMapper.createObjectNode().put("wait_cycle", ticket.getWaitCycle()), now);
                                    return true;
                                }
                                if (TicketStatusEnum.RESOLVED.getValue().equals(ticket.getStatus())
                                        && !now.isBefore(ticket.getConfirmationDueAt())) {
                                    ticket.setStatus(TicketStatusEnum.CLOSED.getValue());
                                    ticket.setClosureReason(
                                            ClosureReasonEnum.CONFIRMATION_TIMEOUT.getValue());
                                    ticket.setClosedAt(now);
                                    stateWriter.save(ticket, now);
                                    append(
                                            ticket, TicketStatusEnum.CLOSED.getValue(),
                                            TicketStatusEnum.RESOLVED.getValue(),
                                            VisibilityEnum.PUBLIC.getValue(),
                                            "处理结果超过72小时未反馈，按期限结单",
                                            objectMapper
                                                    .createObjectNode()
                                                    .put(
                                                            "closure_reason",
                                                            ClosureReasonEnum.CONFIRMATION_TIMEOUT
                                                                    .getValue()),
                                            now);
                                    return true;
                                }
                                return false;
                            },
                            () -> false);
            if (changed) count++;
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean recordAcceptanceOverdue(TicketEntity ticket, LocalDateTime now) {
        if (ticket.getAcceptanceOverdueRecordedAt() != null || !now.isAfter(ticket.getAcceptanceDueAt())) {
            return false;
        }
        ticket.setAcceptanceOverdueRecordedAt(now);
        append(ticket, "ACCEPTANCE_OVERDUE", ticket.getStatus(), VisibilityEnum.INTERNAL.getValue(),
                "已超过首次受理期限", objectMapper.createObjectNode(), now);
        return true;
    }

    /**
     * 在当前工单事务追加系统期限事件与相关通知。
     */
    private void append(
            TicketEntity ticket,
            String type,
            String from,
            String visibility,
            String content,
            ObjectNode metadata,
            LocalDateTime now) {
        timelineService.append(ticket, null, type, null, null, from, visibility, content, metadata, now, null);
    }
}
