package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.ticket.model.entity.DeviceEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ProductModelEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEntitlementEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEventEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.vo.device.DeviceVO;
import com.yangaobo.servicedesk.ticket.model.vo.device.ProductModelVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEntitlementVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEventVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceItemVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketEventVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketVO;

import java.time.ZoneOffset;

/**
 * 工单及支持资源公开投影的业务边界。
 *
 * @author yangaobo
 */
public final class TicketViews {
    /**
     * 禁止实例化映射工具。
     */
    private TicketViews() {
    }

    /**
     * 显式投影ServiceItem安全字段。
     */
    public static ServiceItemVO of(ServiceItemEntity entity) {
        ServiceItemVO result = new ServiceItemVO();
        result.setId(entity.getId());
        result.setServiceCode(entity.getServiceCode());
        result.setCategory(entity.getCategory());
        result.setServiceName(entity.getServiceName());
        result.setSupportScope(entity.getSupportScope());
        result.setOnsiteSupported(entity.getOnsiteSupported());
        result.setStatus(entity.getStatus());
        result.setChangeReason(entity.getChangeReason());
        result.setCreatedAt(
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                entity.getUpdatedAt() == null
                        ? null
                        : entity.getUpdatedAt().toInstant(ZoneOffset.UTC));
        result.setRowVersion(entity.getRowVersion());
        return result;
    }

    /**
     * 显式投影ServiceEntitlement安全字段。
     */
    public static ServiceEntitlementVO of(ServiceEntitlementEntity entity) {
        ServiceEntitlementVO result = new ServiceEntitlementVO();
        result.setId(entity.getId());
        result.setCustomerId(entity.getCustomerId());
        result.setServiceItemId(entity.getServiceItemId());
        result.setStartsOn(entity.getStartsOn());
        result.setEndsOn(entity.getEndsOn());
        result.setStatus(entity.getStatus());
        result.setChangeReason(entity.getChangeReason());
        result.setCreatedAt(
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                entity.getUpdatedAt() == null
                        ? null
                        : entity.getUpdatedAt().toInstant(ZoneOffset.UTC));
        result.setRowVersion(entity.getRowVersion());
        return result;
    }

    /**
     * 显式投影ServiceEvent安全字段。
     */
    public static ServiceEventVO of(ServiceEventEntity entity) {
        ServiceEventVO result = new ServiceEventVO();
        result.setId(entity.getId());
        result.setServiceItemId(entity.getServiceItemId());
        result.setEntitlementId(entity.getEntitlementId());
        result.setActorId(entity.getActorId());
        result.setEventType(entity.getEventType());
        result.setDetails(ServiceAuditViews.details(entity.getDetails()));
        result.setCreatedAt(
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().toInstant(ZoneOffset.UTC));
        return result;
    }

    /**
     * 显式投影ProductModel安全字段。
     */
    public static ProductModelVO of(ProductModelEntity entity) {
        ProductModelVO result = new ProductModelVO();
        result.setId(entity.getId());
        result.setCategory(entity.getCategory());
        result.setBrand(entity.getBrand());
        result.setModelName(entity.getModelName());
        result.setSupportedSystems(entity.getSupportedSystems());
        result.setStatus(entity.getStatus());
        result.setCreatedAt(
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                entity.getUpdatedAt() == null
                        ? null
                        : entity.getUpdatedAt().toInstant(ZoneOffset.UTC));
        result.setRowVersion(entity.getRowVersion());
        return result;
    }

    /**
     * 显式投影Device安全字段。
     */
    public static DeviceVO of(DeviceEntity entity) {
        DeviceVO result = new DeviceVO();
        result.setId(entity.getId());
        result.setCustomerId(entity.getCustomerId());
        result.setProductModelId(entity.getProductModelId());
        result.setAssetCode(entity.getAssetCode());
        result.setSerialNumber(entity.getSerialNumber());
        result.setLocation(entity.getLocation());
        result.setServiceAreaCode(entity.getServiceAreaCode());
        result.setStatus(entity.getStatus());
        result.setCreatedAt(
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                entity.getUpdatedAt() == null
                        ? null
                        : entity.getUpdatedAt().toInstant(ZoneOffset.UTC));
        result.setRowVersion(entity.getRowVersion());
        return result;
    }

    /**
     * 显式投影Ticket安全字段。
     */
    public static TicketVO of(TicketEntity entity) {
        TicketVO result = new TicketVO();
        result.setId(entity.getId());
        result.setTicketNo(entity.getTicketNo());
        result.setCustomerId(entity.getCustomerId());
        result.setRequesterId(entity.getRequesterId());
        result.setServiceItemId(entity.getServiceItemId());
        result.setEntitlementId(entity.getEntitlementId());
        result.setServiceScopeSnapshot(TicketSnapshotViews.scope(entity.getServiceScopeSnapshot()));
        result.setDeviceId(entity.getDeviceId());
        result.setDeviceSnapshot(TicketSnapshotViews.device(entity.getDeviceSnapshot()));
        result.setSupportContext(TicketSnapshotViews.context(entity.getSupportContext()));
        result.setServiceAreaCode(entity.getServiceAreaCode());
        result.setServiceLocation(entity.getServiceLocation());
        result.setTitle(entity.getTitle());
        result.setSymptom(entity.getSymptom());
        result.setAttemptedActions(entity.getAttemptedActions());
        result.setPriority(entity.getPriority());
        result.setStatus(entity.getStatus());
        result.setAssigneeId(entity.getAssigneeId());
        result.setFirstAcceptedAt(
                entity.getFirstAcceptedAt() == null
                        ? null
                        : entity.getFirstAcceptedAt().toInstant(ZoneOffset.UTC));
        result.setAcceptanceDueAt(
                entity.getAcceptanceDueAt() == null
                        ? null
                        : entity.getAcceptanceDueAt().toInstant(ZoneOffset.UTC));
        result.setAcceptanceOverdueRecordedAt(
                entity.getAcceptanceOverdueRecordedAt() == null
                        ? null
                        : entity.getAcceptanceOverdueRecordedAt().toInstant(ZoneOffset.UTC));
        result.setResolutionType(entity.getResolutionType());
        result.setResolvedAt(
                entity.getResolvedAt() == null
                        ? null
                        : entity.getResolvedAt().toInstant(ZoneOffset.UTC));
        result.setConfirmationDueAt(
                entity.getConfirmationDueAt() == null
                        ? null
                        : entity.getConfirmationDueAt().toInstant(ZoneOffset.UTC));
        result.setOnsiteEligible(entity.getOnsiteEligible());
        result.setClosureReason(entity.getClosureReason());
        result.setClosedAt(
                entity.getClosedAt() == null
                        ? null
                        : entity.getClosedAt().toInstant(ZoneOffset.UTC));
        result.setCancelledAt(
                entity.getCancelledAt() == null
                        ? null
                        : entity.getCancelledAt().toInstant(ZoneOffset.UTC));
        result.setCreatedAt(
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().toInstant(ZoneOffset.UTC));
        result.setUpdatedAt(
                entity.getUpdatedAt() == null
                        ? null
                        : entity.getUpdatedAt().toInstant(ZoneOffset.UTC));
        result.setRowVersion(entity.getRowVersion());
        return result;
    }

    /**
     * 显式投影TicketEvent安全字段。
     */
    public static TicketEventVO of(TicketEventEntity entity) {
        TicketEventVO result = new TicketEventVO();
        result.setId(entity.getId());
        result.setTicketId(entity.getTicketId());
        result.setSequenceNumber(entity.getSequenceNumber());
        result.setEventType(entity.getEventType());
        result.setOnsiteBookingId(entity.getOnsiteBookingId());
        result.setActorType(entity.getActorType());
        result.setActorId(entity.getActorId());
        result.setVisibility(entity.getVisibility());
        result.setFromStatus(entity.getFromStatus());
        result.setToStatus(entity.getToStatus());
        result.setContent(entity.getContent());
        result.setMetadata(TicketSnapshotViews.metadata(entity.getMetadata()));
        result.setCreatedAt(
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().toInstant(ZoneOffset.UTC));
        return result;
    }
}
