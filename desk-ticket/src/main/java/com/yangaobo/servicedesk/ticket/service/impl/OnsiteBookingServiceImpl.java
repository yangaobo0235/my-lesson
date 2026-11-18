package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.ActionDraftClient;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteCancelForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteFinishForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteAssignmentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.service.IActionReceiptService;

import java.util.Objects;

import com.yangaobo.servicedesk.ticket.error.BookingErrorCode;
import com.yangaobo.servicedesk.ticket.mapper.OnsiteBookingMapper;
import com.yangaobo.servicedesk.ticket.mapper.OnsiteSlotMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.dto.onsite.OnsiteBookingResult;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.enums.OnsiteBookingStatusEnum;
import com.yangaobo.servicedesk.ticket.model.enums.OnsiteSlotStatusEnum;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteBookingForm;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteBookingVO;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;
import com.yangaobo.servicedesk.ticket.service.IOnsiteBookingService;
import com.yangaobo.servicedesk.ticket.service.ITicketEventService;
import com.yangaobo.servicedesk.ticket.service.OnsiteViews;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;

import java.time.LocalDateTime;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 统一工单、预约、时段锁序以及原键回执与容量生命周期。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class OnsiteBookingServiceImpl extends ServiceImpl<OnsiteBookingMapper, OnsiteBookingEntity>
        implements IOnsiteBookingService {
    private final TicketMapper ticketMapper;
    private final OnsiteSlotMapper slotMapper;
    private final BusinessTransaction transactions;
    private final RequestDigest digest;
    private final ObjectMapper objectMapper;
    private final ITicketEventService timeline;
    private final IActionReceiptService receipts;
    private final IdentityVerificationClient identityClient;
    private final ActionDraftClient draftClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteBookingVO query(IdentityDTO actor, String bookingId) {
        if (!(actor.getRoles().contains("SERVICE_AGENT")
                || actor.getRoles().contains("TECHNICIAN")))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        var booking = baseMapper.selectById(bookingId);
        if (booking == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        readable(actor, ticketMapper.selectById(booking.getTicketId()));
        return OnsiteViews.booking(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<OnsiteSlotVO> slots(IdentityDTO actor, String ticketId, BasePageForm form) {
        TicketEntity ticket = readable(actor, ticketMapper.selectById(ticketId));
        eligible(ticket);
        LocalDateTime now = ticketMapper.decisionTime();
        long total = slotMapper.countBookable(ticket.getServiceAreaCode(), now);
        var records = slotMapper.listBookable(ticket.getServiceAreaCode(), now, form.getPageSize(), form.offset())
                .stream().map(OnsiteViews::slot).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<OnsiteBookingVO> history(IdentityDTO actor, String ticketId, BasePageForm form) {
        readable(actor, ticketMapper.selectById(ticketId));
        long total = baseMapper.countHistory(ticketId);
        var records = baseMapper.listHistory(ticketId, form.getPageSize(), form.offset())
                .stream().map(OnsiteViews::booking).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteBookingVO byRequest(IdentityDTO actor, String ticketId, String key) {
        readable(actor, ticketMapper.selectById(ticketId));
        var receipt = receipts.byAction(actor, key);
        if (!ticketId.equals(receipt.getTicketId()) || !"BOOK_ONSITE".equals(receipt.getActionType()))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        OnsiteBookingEntity value = baseMapper.selectById(receipt.getBookingId());
        if (value == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return OnsiteViews.booking(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteBookingResult book(IdentityDTO actor, String ticketId, OnsiteBookingForm form) {
        String hash = digest.actionHash("BOOK_ONSITE", ticketId, form);
        ActionReceiptEntity previous = receipts.replay(actor, form.getClientRequestId(), hash);
        if (previous == null) previous = receipts.replayDraft(actor, form.getSourceDraftRootId(), hash);
        if (previous != null) return bookingResult(previous, false);
        TicketEntity initial = readable(actor, ticketMapper.selectById(ticketId));
        requester(actor, initial);
        eligible(initial);
        verifyTechnician(actor, initial, initial.getAssigneeId());
        var validation = draftClient.validate(actor, form.getSourceDraftId(), "BOOK_ONSITE",
                initial.getCustomerId(), initial.getServiceItemId(), initial.getDeviceId(), ticketId,
                form.getRowVersion(), objectMapper.valueToTree(form));
        return transactions.execute(
                () -> {
                    TicketEntity ticket = readable(actor, ticketMapper.lock(ticketId));
                    requester(actor, ticket);
                    ActionReceiptEntity old = receipts.replay(actor, form.getClientRequestId(), hash);
                    if (old == null) old = receipts.replayDraft(actor, form.getSourceDraftRootId(), hash);
                    if (old != null) return bookingResult(old, false);
                    if (!ticket.getRowVersion().equals(form.getRowVersion())
                            || !Objects.equals(initial.getAssigneeId(), ticket.getAssigneeId()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    eligible(ticket);
                    OnsiteBookingEntity active =
                            baseMapper.selectOne(
                                    new LambdaQueryWrapper<OnsiteBookingEntity>()
                                            .eq(OnsiteBookingEntity::getTicketId, ticketId)
                                            .eq(
                                                    OnsiteBookingEntity::getStatus,
                                                    OnsiteBookingStatusEnum.CONFIRMED.getValue()));
                    if (active != null) {
                        baseMapper.lock(active.getId());
                        throw new BusinessException(BookingErrorCode.BOOKING_NOT_ALLOWED);
                    }
                    OnsiteSlotEntity slot = slotMapper.lock(form.getSlotId());
                    if (slot == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    if (!slot.getServiceAreaCode().equals(ticket.getServiceAreaCode()))
                        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    LocalDateTime now = ticketMapper.decisionTime();
                    draftClient.requireValid(validation, now);
                    boolean closed =
                            !OnsiteSlotStatusEnum.PUBLISHED.getValue().equals(slot.getStatus())
                                    || now.isBefore(slot.getBookingOpensAt())
                                    || !now.isBefore(slot.getBookingClosesAt());
                    boolean full = slot.getReservedCount() >= slot.getCapacity();
                    OnsiteBookingEntity booking = new OnsiteBookingEntity();
                    booking.setId(IdentifierGenerator.next());
                    booking.setTicketId(ticketId);
                    booking.setSlotId(slot.getId());
                    booking.setServiceAreaCode(ticket.getServiceAreaCode());
                    booking.setVisitAddress(ticket.getServiceLocation());
                    booking.setClientRequestId(form.getClientRequestId());
                    booking.setRequestHash(hash);
                    booking.setRowVersion(0L);
                    TicketEventEntity event = null;
                    booking.setCreatedAt(now);
                    booking.setUpdatedAt(now);
                    if (closed || full) {
                        booking.setStatus(OnsiteBookingStatusEnum.REJECTED.getValue());
                        booking.setResultCode(closed ? "SLOT_CLOSED" : "SOLD_OUT");
                        booking.setEndedAt(now);
                        baseMapper.insert(booking);
                    } else {
                        booking.setStatus(OnsiteBookingStatusEnum.CONFIRMED.getValue());
                        booking.setConfirmedAt(now);
                        booking.setAssignedTechnicianId(ticket.getAssigneeId());
                        slot.setReservedCount(slot.getReservedCount() + 1);
                        slot.setRowVersion(slot.getRowVersion() + 1);
                        slot.setUpdatedAt(now);
                        slotMapper.updateById(slot);
                        baseMapper.insert(booking);
                        event = event(actor, ticket, booking, "ONSITE_BOOKED", "已确认上门预约", now);
                    }
                    draftClient.requireValid(validation, ticketMapper.decisionTime());
                    receipts.record(actor, ticket, booking, event, "BOOK_ONSITE", form.getClientRequestId(), hash,
                            form.getSourceDraftId(), validation == null ? null : validation.getDraftRootId(), now);
                    return OnsiteBookingResult.of(OnsiteViews.booking(booking), true);
                },
                () -> {
                    readable(actor, ticketMapper.selectById(ticketId));
                    ActionReceiptEntity old = receipts.replay(actor, form.getClientRequestId(), hash);
                    if (old == null) old = receipts.replayDraft(actor, form.getSourceDraftRootId(), hash);
                    return old == null ? null : bookingResult(old, false);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO cancel(IdentityDTO actor, String ticketId, String bookingId, OnsiteCancelForm form) {
        return change(actor, ticketId, bookingId, form, "CANCEL_BOOKING", (ticket, booking, slot, now) -> {
            if (!handler(actor, ticket) && !actor.getRoles().contains("SERVICE_AGENT")) requester(actor, ticket);
            if (!now.isBefore(slot.getStartsAt())) throw new BusinessException(CommonErrorCode.CONFLICT);
            booking.setStatus("CANCELLED");
            booking.setCancelReason(form.getReason());
            booking.setEndedAt(now);
            slot.setReservedCount(slot.getReservedCount() - 1);
            slot.setRowVersion(slot.getRowVersion() + 1);
            slot.setUpdatedAt(now);
            if (slotMapper.updateById(slot) != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
            return "已取消上门预约：" + form.getReason();
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO finish(IdentityDTO actor, String ticketId, String bookingId, OnsiteFinishForm form) {
        ActionReceiptEntity old = receipts.replay(actor, form.getClientActionId(), digest.actionHash("FINISH_BOOKING", ticketId + ":" + bookingId, form));
        if (old != null) return receipts.view(old);
        TicketEntity initial = readable(actor, ticketMapper.selectById(ticketId));
        if (!handler(actor, initial)) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        verifyTechnician(actor, initial, form.getPerformedBy());
        return change(actor, ticketId, bookingId, form, "FINISH_BOOKING", (ticket, booking, slot, now) -> {
            if (!handler(actor, ticket)) throw new BusinessException(CommonErrorCode.FORBIDDEN);
            if (now.isBefore(slot.getStartsAt()) || !Set.of("VISITED", "NOT_PERFORMED").contains(form.getFinishResult()))
                throw new BusinessException(CommonErrorCode.CONFLICT);
            booking.setStatus("FINISHED");
            booking.setFinishResult(form.getFinishResult());
            booking.setPerformedBy(form.getPerformedBy());
            booking.setFinishNote(form.getFinishNote());
            booking.setEndedAt(now);
            return "已登记实际履约：" + form.getFinishResult() + "；" + form.getFinishNote();
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO assign(IdentityDTO actor, String ticketId, String bookingId, OnsiteAssignmentForm form) {
        ActionReceiptEntity old = receipts.replay(actor, form.getClientActionId(), digest.actionHash("ASSIGN_BOOKING", ticketId + ":" + bookingId, form));
        if (old != null) return receipts.view(old);
        TicketEntity initial = readable(actor, ticketMapper.selectById(ticketId));
        if (!handler(actor, initial) && !actor.getRoles().contains("SERVICE_AGENT"))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        verifyTechnician(actor, initial, form.getTechnicianId());
        return change(actor, ticketId, bookingId, form, "ASSIGN_BOOKING", (ticket, booking, slot, now) -> {
            if (!handler(actor, ticket) && !actor.getRoles().contains("SERVICE_AGENT"))
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            if (form.getTechnicianId().equals(booking.getAssignedTechnicianId()))
                throw new BusinessException(CommonErrorCode.CONFLICT);
            booking.setAssignedTechnicianId(form.getTechnicianId());
            return "调整现场负责人：" + form.getReason();
        });
    }

    /**
     * 预约版本与工单版本分开；统一锁序后提交状态、容量、事件及不可变回执。
     */
    private TicketActionReceiptVO change(IdentityDTO actor, String ticketId, String bookingId,
                                         TicketActionForm form, String type, BookingChange action) {
        String hash = digest.actionHash(type, ticketId + ":" + bookingId, form);
        return transactions.execute(() -> {
            TicketEntity ticket = readable(actor, ticketMapper.lock(ticketId));
            ActionReceiptEntity old = receipts.replay(actor, form.getClientActionId(), hash);
            if (old != null) return receipts.view(old);
            OnsiteBookingEntity booking = baseMapper.lock(bookingId);
            if (booking == null || !ticketId.equals(booking.getTicketId()))
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            if (!booking.getRowVersion().equals(form.getRowVersion()) || !"CONFIRMED".equals(booking.getStatus()))
                throw new BusinessException(CommonErrorCode.CONFLICT);
            OnsiteSlotEntity slot = slotMapper.lock(booking.getSlotId());
            LocalDateTime now = ticketMapper.decisionTime();
            String content = action.apply(ticket, booking, slot, now);
            booking.setRowVersion(Math.addExact(booking.getRowVersion(), 1));
            booking.setUpdatedAt(now);
            if (baseMapper.updateById(booking) != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
            String eventType = switch (type) {
                case "CANCEL_BOOKING" -> "ONSITE_BOOKING_CANCELLED";
                case "FINISH_BOOKING" -> "ONSITE_FINISHED";
                case "ASSIGN_BOOKING" -> "ONSITE_ASSIGNED";
                default -> throw new IllegalArgumentException("未登记预约动作");
            };
            TicketEventEntity event = event(actor, ticket, booking, eventType, content, now);
            return receipts.view(receipts.record(actor, ticket, booking, event, type, form.getClientActionId(), hash, null, null, now));
        }, () -> {
            readable(actor, ticketMapper.selectById(ticketId));
            ActionReceiptEntity old = receipts.replay(actor, form.getClientActionId(), hash);
            return old == null ? null : receipts.view(old);
        });
    }

    /**
     * 外部资格在事务外核验，网络失败不登记履约或扣容量。
     */
    private void verifyTechnician(IdentityDTO actor, TicketEntity ticket, String technicianId) {
        if (technicianId == null || !identityClient.eligibility(actor, ticket.getCustomerId(), technicianId).isAssigneeAvailable())
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 当前技术处理人具有登记实际结果的职责。
     */
    private boolean handler(IdentityDTO actor, TicketEntity ticket) {
        return actor.getRoles().contains("TECHNICIAN") && actor.getOwnerId().equals(ticket.getAssigneeId());
    }

    /**
     * 原申请查询返回同一预约资源；原提交版本与状态由统一回执读取。
     */
    private OnsiteBookingResult bookingResult(ActionReceiptEntity receipt, boolean created) {
        return OnsiteBookingResult.of(OnsiteViews.booking(baseMapper.selectById(receipt.getBookingId())), created);
    }

    /**
     * 本地持锁领域动作，不允许在此调用外部服务。
     *
     * @author yangaobo
     */
    @FunctionalInterface
    private interface BookingChange {
        /**
         * 更新预约及必要容量，返回真实事件说明。
         */
        String apply(TicketEntity ticket, OnsiteBookingEntity booking, OnsiteSlotEntity slot, LocalDateTime now);
    }

    /**
     * 调用者持有工单锁时追加事件并推进工单版本。
     */
    private TicketEventEntity event(
            IdentityDTO actor,
            TicketEntity ticket,
            OnsiteBookingEntity booking,
            String type,
            String content,
            LocalDateTime now) {
        ticket.setRowVersion(ticket.getRowVersion() + 1);
        ticket.setUpdatedAt(now);
        ticketMapper.updateById(ticket);
        return timeline.append(
                ticket,
                actor,
                type,
                null,
                null,
                ticket.getStatus(),
                "PUBLIC",
                content,
                objectMapper
                        .createObjectNode()
                        .put("booking_id", booking.getId())
                        .put("slot_id", booking.getSlotId())
                        .put("finish_result", booking.getFinishResult())
                        .put("assigned_technician_id", booking.getAssignedTechnicianId())
                        .put("performed_by", booking.getPerformedBy())
                        .put("booking_row_version", booking.getRowVersion()),
                now,
                booking.getId());
    }

    /**
     * 当前读取权限不由预约记录或管理员角色扩大。
     */
    private TicketEntity readable(IdentityDTO actor, TicketEntity ticket) {
        if (ticket == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (!SupportAuthorization.canRead(actor, ticket))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        return ticket;
    }

    /**
     * 申请只由原客户本人发起。
     */
    private void requester(IdentityDTO actor, TicketEntity ticket) {
        SupportAuthorization.role(actor, "CUSTOMER_USER");
        if (!actor.getOwnerId().equals(ticket.getRequesterId()))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    /**
     * 服务到期不撤销既有受理；使用固定受理快照核对现场资格。
     */
    private void eligible(TicketEntity ticket) {
        if (!Set.of("PROCESSING", "WAITING_CUSTOMER").contains(ticket.getStatus())
                || !ticket.getOnsiteEligible()
                || ticket.getServiceItemId() == null
                || !ticket.getServiceScopeSnapshot().path("onsite_supported").asBoolean()
                || ticket.getServiceAreaCode() == null
                || ticket.getServiceLocation() == null
                || ticket.getServiceLocation().isBlank())
            throw new BusinessException(BookingErrorCode.BOOKING_NOT_ALLOWED);
    }

}
