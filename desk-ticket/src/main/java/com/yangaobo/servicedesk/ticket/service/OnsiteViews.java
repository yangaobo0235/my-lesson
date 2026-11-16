package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteBookingVO;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;

import java.time.ZoneOffset;

/**
 * 现场服务的明确公开字段投影。
 *
 * @author yangaobo
 */
public final class OnsiteViews {
    /**
     * 不创建无状态映射实例。
     */
    private OnsiteViews() {
    }

    /**
     * 输出真实时段容量。
     */
    public static OnsiteSlotVO slot(OnsiteSlotEntity value) {
        OnsiteSlotVO result = new OnsiteSlotVO();
        result.setId(value.getId());
        result.setServiceAreaCode(value.getServiceAreaCode());
        result.setStartsAt(
                value.getStartsAt() == null ? null : value.getStartsAt().toInstant(ZoneOffset.UTC));
        result.setEndsAt(
                value.getEndsAt() == null ? null : value.getEndsAt().toInstant(ZoneOffset.UTC));
        result.setBookingOpensAt(
                value.getBookingOpensAt() == null
                        ? null
                        : value.getBookingOpensAt().toInstant(ZoneOffset.UTC));
        result.setBookingClosesAt(
                value.getBookingClosesAt() == null
                        ? null
                        : value.getBookingClosesAt().toInstant(ZoneOffset.UTC));
        result.setCapacity(value.getCapacity());
        result.setReservedCount(value.getReservedCount());
        result.setStatus(value.getStatus());
        result.setPublishedAt(
                value.getPublishedAt() == null
                        ? null
                        : value.getPublishedAt().toInstant(ZoneOffset.UTC));
        result.setClosedAt(
                value.getClosedAt() == null ? null : value.getClosedAt().toInstant(ZoneOffset.UTC));
        result.setCreatedAt(
                value.getCreatedAt() == null
                        ? null
                        : value.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                value.getUpdatedAt() == null
                        ? null
                        : value.getUpdatedAt().toInstant(ZoneOffset.UTC));
        result.setRowVersion(value.getRowVersion());
        return result;
    }

    /**
     * 输出当前预约状态，不把履约伪装成取消。
     */
    public static OnsiteBookingVO booking(OnsiteBookingEntity value) {
        OnsiteBookingVO result = new OnsiteBookingVO();
        result.setId(value.getId());
        result.setTicketId(value.getTicketId());
        result.setSlotId(value.getSlotId());
        result.setServiceAreaCode(value.getServiceAreaCode());
        result.setVisitAddress(value.getVisitAddress());
        result.setClientRequestId(value.getClientRequestId());
        result.setStatus(value.getStatus());
        result.setResultCode(value.getResultCode());
        result.setFinishResult(value.getFinishResult());
        result.setConfirmedAt(
                value.getConfirmedAt() == null
                        ? null
                        : value.getConfirmedAt().toInstant(ZoneOffset.UTC));
        result.setEndedAt(
                value.getEndedAt() == null ? null : value.getEndedAt().toInstant(ZoneOffset.UTC));
        result.setCreatedAt(
                value.getCreatedAt() == null
                        ? null
                        : value.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                value.getUpdatedAt() == null
                        ? null
                        : value.getUpdatedAt().toInstant(ZoneOffset.UTC));
        return result;
    }
}
