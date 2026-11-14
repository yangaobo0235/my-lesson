package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteBookingVO;

/**
 * 现场服务的明确公开字段投影。
 *
 * @author yangaobo
 */
public final class OnsiteViews {
    /** 不创建无状态映射实例。 */
    private OnsiteViews() {}
    /** 输出真实时段容量。 */
    public static OnsiteSlotVO slot(OnsiteSlotEntity value) {
        OnsiteSlotVO result = new OnsiteSlotVO();
        result.setId(value.getId());
        result.setServiceAreaCode(value.getServiceAreaCode());
        result.setStartsAt(value.getStartsAt() == null ? null : value.getStartsAt().toInstant(java.time.ZoneOffset.UTC));
        result.setEndsAt(value.getEndsAt() == null ? null : value.getEndsAt().toInstant(java.time.ZoneOffset.UTC));
        result.setBookingOpensAt(value.getBookingOpensAt() == null ? null : value.getBookingOpensAt().toInstant(java.time.ZoneOffset.UTC));
        result.setBookingClosesAt(value.getBookingClosesAt() == null ? null : value.getBookingClosesAt().toInstant(java.time.ZoneOffset.UTC));
        result.setCapacity(value.getCapacity());
        result.setReservedCount(value.getReservedCount());
        result.setStatus(value.getStatus());
        result.setPublishedAt(value.getPublishedAt() == null ? null : value.getPublishedAt().toInstant(java.time.ZoneOffset.UTC));
        result.setClosedAt(value.getClosedAt() == null ? null : value.getClosedAt().toInstant(java.time.ZoneOffset.UTC));
        result.setCreatedAt(value.getCreatedAt() == null ? null : value.getCreatedAt().toInstant(java.time.ZoneOffset.UTC));
        result.setUpdatedAt(value.getUpdatedAt() == null ? null : value.getUpdatedAt().toInstant(java.time.ZoneOffset.UTC));
        result.setRowVersion(value.getRowVersion());
        return result;
    }
    /** 输出当前预约状态，不把履约伪装成取消。 */
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
        result.setConfirmedAt(value.getConfirmedAt() == null ? null : value.getConfirmedAt().toInstant(java.time.ZoneOffset.UTC));
        result.setEndedAt(value.getEndedAt() == null ? null : value.getEndedAt().toInstant(java.time.ZoneOffset.UTC));
        result.setCreatedAt(value.getCreatedAt() == null ? null : value.getCreatedAt().toInstant(java.time.ZoneOffset.UTC));
        result.setUpdatedAt(value.getUpdatedAt() == null ? null : value.getUpdatedAt().toInstant(java.time.ZoneOffset.UTC));
        return result;
    }
}
