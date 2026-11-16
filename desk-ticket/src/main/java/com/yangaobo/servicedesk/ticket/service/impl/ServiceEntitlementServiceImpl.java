package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.error.SupportErrorCode;
import com.yangaobo.servicedesk.ticket.mapper.ServiceEntitlementMapper;
import com.yangaobo.servicedesk.ticket.mapper.ServiceItemMapper;
import com.yangaobo.servicedesk.ticket.model.dto.support.ServiceAdmission;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEntitlementEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEventEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;
import com.yangaobo.servicedesk.ticket.model.form.support.EntitlementGrantForm;
import com.yangaobo.servicedesk.ticket.model.form.support.EntitlementUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceStatusForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.EntitledServiceVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEntitlementVO;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.IServiceEventService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import com.yangaobo.servicedesk.ticket.service.TicketViews;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户服务资格的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceEntitlementServiceImpl
        extends ServiceImpl<ServiceEntitlementMapper, ServiceEntitlementEntity>
        implements IServiceEntitlementService {
    private final ServiceItemMapper itemMapper;
    private final IServiceEventService events;
    private final RequestDigest digest;
    private final BusinessTransaction transactions;
    private final IdentityVerificationClient identityClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<ServiceEntitlementVO> listEntitlements(IdentityDTO actor, SupportQueryForm form) {
        SupportAuthorization.role(actor, "SERVICE_AGENT");
        Set<String> customerIds = actor.getCustomerIds();
        if (form.getCustomerId() != null) {
            SupportAuthorization.agent(actor, form.getCustomerId());
            customerIds = Set.of(form.getCustomerId());
        }
        long total = baseMapper.countEntitlements(customerIds, form.getStatus());
        var records = baseMapper.listEntitlements(customerIds, form.getStatus(), form.getPageSize(), form.offset())
                .stream().map(TicketViews::of).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceEntitlementVO query(IdentityDTO actor, String id) {
        ServiceEntitlementEntity row = baseMapper.selectById(id);
        if (row == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        SupportAuthorization.agent(actor, row.getCustomerId());
        return TicketViews.of(row);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceEntitlementVO grant(IdentityDTO actor, EntitlementGrantForm form) {
        SupportAuthorization.agent(actor, form.getCustomerId());
        String hash = digest.hash("ENTITLEMENT_GRANTED", null, form);
        ServiceEntitlementVO existing = receipt(actor, form.getClientActionId(), hash);
        if (existing != null) return existing;
        dates(form.getStartsOn(), form.getEndsOn());
        if (!identityClient.eligibility(actor, form.getCustomerId(), null).isCustomerActive())
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return transactions.execute(
                () -> {
                    ServiceEntitlementVO replay = receipt(actor, form.getClientActionId(), hash);
                    if (replay != null) return replay;
                    ServiceItemEntity item = itemMapper.lock(form.getServiceItemId());
                    active(item);
                    ServiceEntitlementEntity row = new ServiceEntitlementEntity();
                    row.setId(IdentifierGenerator.next());
                    row.setCustomerId(form.getCustomerId());
                    row.setServiceItemId(item.getId());
                    row.setStartsOn(form.getStartsOn());
                    row.setEndsOn(form.getEndsOn());
                    row.setStatus("ACTIVE");
                    row.setGrantedBy(actor.getOwnerId());
                    row.setUpdatedBy(actor.getOwnerId());
                    row.setChangeReason(form.getReason());
                    row.setRowVersion(0L);
                    row.setCreatedAt(baseMapper.decisionTime());
                    row.setUpdatedAt(row.getCreatedAt());
                    baseMapper.insert(row);
                    ServiceEntitlementVO result = TicketViews.of(row);
                    events.append(
                            actor,
                            item.getId(),
                            row.getId(),
                            form.getClientActionId(),
                            hash,
                            "ENTITLEMENT_GRANTED",
                            null,
                            result,
                            form.getReason());
                    return result;
                },
                () -> receipt(actor, form.getClientActionId(), hash));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceEntitlementVO updateEntitlement(IdentityDTO actor, EntitlementUpdateForm form) {
        dates(form.getStartsOn(), form.getEndsOn());
        return mutate(
                actor,
                form.getId(),
                form.getRowVersion(),
                form.getClientActionId(),
                "ENTITLEMENT_UPDATED",
                form,
                form.getReason(),
                row -> {
                    row.setStartsOn(form.getStartsOn());
                    row.setEndsOn(form.getEndsOn());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceEntitlementVO setSuspended(
            IdentityDTO actor, ServiceStatusForm form, boolean suspended) {
        return mutate(
                actor,
                form.getId(),
                form.getRowVersion(),
                form.getClientActionId(),
                suspended ? "ENTITLEMENT_SUSPENDED" : "ENTITLEMENT_RESUMED",
                form,
                form.getReason(),
                row -> row.setStatus(suspended ? "SUSPENDED" : "ACTIVE"));
    }

    /**
     * 每个资格变更沿项目→资格锁序，回执优先于新资格及版本。
     */
    private ServiceEntitlementVO mutate(
            IdentityDTO actor,
            String id,
            long version,
            String key,
            String type,
            Object form,
            String reason,
            Consumer<ServiceEntitlementEntity> change) {
        String hash = digest.hash(type, id, form);
        ServiceEntitlementVO replay = receipt(actor, key, hash);
        if (replay != null) return replay;
        ServiceEntitlementVO target = query(actor, id);
        if (!identityClient.eligibility(actor, target.getCustomerId(), null).isCustomerActive())
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return transactions.execute(
                () -> {
                    ServiceEntitlementVO existing = receipt(actor, key, hash);
                    if (existing != null) return existing;
                    ServiceItemEntity item = itemMapper.lock(target.getServiceItemId());
                    ServiceEntitlementEntity row = baseMapper.lock(id);
                    existing = receipt(actor, key, hash);
                    if (existing != null) return existing;
                    SupportAuthorization.agent(actor, row.getCustomerId());
                    if (row.getRowVersion() != version)
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    if (!"ENTITLEMENT_SUSPENDED".equals(type)) active(item);
                    ServiceEntitlementVO before = TicketViews.of(row);
                    change.accept(row);
                    row.setUpdatedBy(actor.getOwnerId());
                    row.setChangeReason(reason);
                    row.setUpdatedAt(baseMapper.decisionTime());
                    row.setRowVersion(version + 1);
                    if (baseMapper.update(
                            row,
                            new LambdaUpdateWrapper<ServiceEntitlementEntity>()
                                    .eq(ServiceEntitlementEntity::getId, id)
                                    .eq(ServiceEntitlementEntity::getRowVersion, version))
                            != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
                    ServiceEntitlementVO result = TicketViews.of(row);
                    events.append(actor, item.getId(), id, key, hash, type, before, result, reason);
                    return result;
                },
                () -> receipt(actor, key, hash));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ServiceAdmission admit(String customerId, String serviceItemId) {
        String target =
                serviceItemId == null ? baseMapper.admissionCandidate(customerId) : serviceItemId;
        if (target == null) throw new BusinessException(SupportErrorCode.SERVICE_NOT_COVERED);
        ServiceItemEntity item = itemMapper.lock(target);
        active(item);
        ServiceEntitlementEntity row = baseMapper.lockCustomer(customerId, target);
        LocalDateTime now = baseMapper.decisionTime();
        LocalDate today = now.plusHours(8).toLocalDate();
        if (row == null
                || !"ACTIVE".equals(row.getStatus())
                || today.isBefore(row.getStartsOn())
                || today.isAfter(row.getEndsOn()))
            throw new BusinessException(SupportErrorCode.SERVICE_NOT_COVERED);
        ServiceAdmission admission = new ServiceAdmission();
        admission.setItem(item);
        admission.setEntitlement(row);
        admission.setDecisionTime(now);
        return admission;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<EntitledServiceVO> available(IdentityDTO actor, SupportQueryForm form) {
        String customer = SupportAuthorization.customer(actor, form.getCustomerId());
        LocalDate today = baseMapper.decisionTime().plusHours(8).toLocalDate();
        long total = baseMapper.countAvailable(customer, today);
        List<ServiceEntitlementEntity> rows = baseMapper.listAvailable(customer, today, form.getPageSize(), form.offset());
        Map<String, ServiceItemEntity> items =
                rows.isEmpty()
                        ? Map.of()
                        : itemMapper
                        .selectByIds(
                                rows.stream()
                                        .map(ServiceEntitlementEntity::getServiceItemId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(ServiceItemEntity::getId, item -> item));

        long pageTotal = total;

        var records =
                rows.stream()
                        .map(
                                row -> {
                                    EntitledServiceVO view = new EntitledServiceVO();
                                    view.setItem(TicketViews.of(items.get(row.getServiceItemId())));
                                    view.setStartsOn(row.getStartsOn());
                                    view.setEndsOn(row.getEndsOn());
                                    return view;
                                })
                        .toList();
        return PageVO.of(records, pageTotal, form);
    }

    /**
     * 已提交请求不重新核验新日期或新状态。
     */
    private ServiceEntitlementVO receipt(IdentityDTO actor, String key, String hash) {
        ServiceEventEntity event = events.receipt(actor, key, hash);
        return event == null ? null : query(actor, event.getEntitlementId());
    }

    /**
     * 日期必须是一段连续有效区间。
     */
    private void dates(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 新业务仅允许启用项目。
     */
    private void active(ServiceItemEntity item) {
        if (item == null || !"ACTIVE".equals(item.getStatus()))
            throw new BusinessException(SupportErrorCode.SERVICE_NOT_COVERED);
    }
}
