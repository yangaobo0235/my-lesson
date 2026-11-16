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
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.error.BookingErrorCode;
import com.yangaobo.servicedesk.ticket.mapper.OnsiteBookingMapper;
import com.yangaobo.servicedesk.ticket.mapper.OnsiteSlotMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.dto.onsite.OnsiteBookingResult;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.enums.OnsiteBookingStatusEnum;
import com.yangaobo.servicedesk.ticket.model.enums.OnsiteFinishResultEnum;
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
        OnsiteBookingEntity value = receipt(ticketId, key, null);
        if (value == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return OnsiteViews.booking(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteBookingResult book(IdentityDTO actor, String ticketId, OnsiteBookingForm form) {
        String hash = digest.hash("ONSITE_BOOK", ticketId, form);
        return transactions.execute(
                () -> {
                    TicketEntity ticket = readable(actor, ticketMapper.lock(ticketId));
                    requester(actor, ticket);
                    OnsiteBookingEntity old = receipt(ticketId, form.getClientRequestId(), hash);
                    if (old != null) return OnsiteBookingResult.of(OnsiteViews.booking(old), false);
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
                        slot.setReservedCount(slot.getReservedCount() + 1);
                        slot.setRowVersion(slot.getRowVersion() + 1);
                        slot.setUpdatedAt(now);
                        slotMapper.updateById(slot);
                        baseMapper.insert(booking);
                        event(actor, ticket, booking, "ONSITE_BOOKED", "已确认上门预约", now);
                    }
                    return OnsiteBookingResult.of(OnsiteViews.booking(booking), true);
                },
                () -> {
                    readable(actor, ticketMapper.selectById(ticketId));
                    OnsiteBookingEntity old = receipt(ticketId, form.getClientRequestId(), hash);
                    return old == null
                            ? null
                            : OnsiteBookingResult.of(OnsiteViews.booking(old), false);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteBookingVO end(
            IdentityDTO actor, String ticketId, String bookingId, String finishResult) {
        return transactions.execute(
                () -> {
                    TicketEntity ticket = readable(actor, ticketMapper.lock(ticketId));
                    boolean handler =
                            actor.getRoles().contains("TECHNICIAN")
                                    && actor.getOwnerId().equals(ticket.getAssigneeId());
                    if (finishResult != null && !handler)
                        throw new BusinessException(CommonErrorCode.FORBIDDEN);
                    if (finishResult == null
                            && !handler
                            && !actor.getRoles().contains("SERVICE_AGENT"))
                        requester(actor, ticket);
                    OnsiteBookingEntity booking = baseMapper.lock(bookingId);
                    if (booking == null || !ticketId.equals(booking.getTicketId()))
                        throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    if (OnsiteBookingStatusEnum.FINISHED.getValue().equals(booking.getStatus())) {
                        if (finishResult != null && !finishResult.equals(booking.getFinishResult()))
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                        return OnsiteViews.booking(booking);
                    }
                    if (!OnsiteBookingStatusEnum.CONFIRMED.getValue().equals(booking.getStatus())) {
                        if (finishResult != null)
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                        return OnsiteViews.booking(booking);
                    }
                    OnsiteSlotEntity slot = slotMapper.lock(booking.getSlotId());
                    LocalDateTime now = ticketMapper.decisionTime();
                    if (finishResult == null) {
                        if (!now.isBefore(slot.getStartsAt()))
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                        booking.setStatus(OnsiteBookingStatusEnum.CANCELLED.getValue());
                        slot.setReservedCount(slot.getReservedCount() - 1);
                        slot.setRowVersion(slot.getRowVersion() + 1);
                        slot.setUpdatedAt(now);
                        slotMapper.updateById(slot);
                    } else {
                        if (now.isBefore(slot.getStartsAt())
                                || !Set.of(
                                        OnsiteFinishResultEnum.VISITED.getValue(),
                                        OnsiteFinishResultEnum.NOT_PERFORMED.getValue())
                                .contains(finishResult))
                            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                        booking.setStatus(OnsiteBookingStatusEnum.FINISHED.getValue());
                        booking.setFinishResult(finishResult);
                    }
                    booking.setEndedAt(now);
                    booking.setUpdatedAt(now);
                    baseMapper.updateById(booking);
                    event(
                            actor,
                            ticket,
                            booking,
                            finishResult == null ? "ONSITE_BOOKING_CANCELLED" : "ONSITE_FINISHED",
                            finishResult == null ? "已取消上门预约并回补名额" : "已登记实际履约：" + finishResult,
                            now);
                    return OnsiteViews.booking(booking);
                },
                () -> null);
    }

    /**
     * 调用者持有工单锁时追加事件并推进工单版本。
     */
    private void event(
            IdentityDTO actor,
            TicketEntity ticket,
            OnsiteBookingEntity booking,
            String type,
            String content,
            LocalDateTime now) {
        ticket.setRowVersion(ticket.getRowVersion() + 1);
        ticket.setUpdatedAt(now);
        ticketMapper.updateById(ticket);
        timeline.append(
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
                        .put("finish_result", booking.getFinishResult()),
                now,
                booking.getId());
    }

    /**
     * 原键先比语义，不重新使用当前容量决定结果。
     */
    private OnsiteBookingEntity receipt(String ticketId, String key, String hash) {
        OnsiteBookingEntity value =
                baseMapper.selectOne(
                        new LambdaQueryWrapper<OnsiteBookingEntity>()
                                .eq(OnsiteBookingEntity::getTicketId, ticketId)
                                .eq(OnsiteBookingEntity::getClientRequestId, key));
        if (value != null && hash != null && !hash.equals(value.getRequestHash()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return value;
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
