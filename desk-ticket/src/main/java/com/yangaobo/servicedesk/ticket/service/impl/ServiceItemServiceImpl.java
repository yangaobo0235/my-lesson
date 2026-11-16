package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.mapper.ServiceItemMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEventEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceItemCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceItemUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceStatusForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceItemVO;
import com.yangaobo.servicedesk.ticket.service.IServiceEventService;
import com.yangaobo.servicedesk.ticket.service.IServiceItemService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import com.yangaobo.servicedesk.ticket.service.TicketViews;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * IT服务项目的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceItemServiceImpl extends ServiceImpl<ServiceItemMapper, ServiceItemEntity>
        implements IServiceItemService {
    private final IServiceEventService events;
    private final RequestDigest digest;
    private final BusinessTransaction transactions;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<ServiceItemVO> listItems(IdentityDTO actor, SupportQueryForm form) {
        if (!actor.getRoles().contains("SERVICE_AGENT")
                && !actor.getRoles().contains("KNOWLEDGE_ADMIN"))
            SupportAuthorization.administrator(actor);
        long total = baseMapper.countItems(form);
        var records = baseMapper.listItems(form, form.getPageSize(), form.offset())
                .stream().map(TicketViews::of).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceItemVO query(IdentityDTO actor, String id) {
        if (!actor.getRoles().contains("SERVICE_AGENT")) SupportAuthorization.administrator(actor);
        ServiceItemEntity item = baseMapper.selectById(id);
        if (item == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return TicketViews.of(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceItemVO create(IdentityDTO actor, ServiceItemCreateForm form) {
        SupportAuthorization.administrator(actor);
        form.setServiceCode(form.getServiceCode().strip().toUpperCase(Locale.ROOT));
        form.setServiceName(form.getServiceName().strip());
        form.setSupportScope(form.getSupportScope().strip());
        String hash = digest.hash("service-item:add", null, form);
        return transactions.execute(
                () -> {
                    ServiceItemVO receipt = receipt(actor, form.getClientActionId(), hash);
                    if (receipt != null) return receipt;
                    ServiceItemEntity item = new ServiceItemEntity();
                    item.setId(IdentifierGenerator.next());
                    item.setServiceCode(form.getServiceCode());
                    item.setCategory(form.getCategory());
                    item.setServiceName(form.getServiceName());
                    item.setSupportScope(form.getSupportScope());
                    item.setOnsiteSupported(form.getOnsiteSupported());
                    item.setStatus("ACTIVE");
                    item.setCreatedBy(actor.getOwnerId());
                    item.setUpdatedBy(actor.getOwnerId());
                    item.setChangeReason(form.getReason());
                    item.setRowVersion(0L);
                    item.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
                    item.setUpdatedAt(item.getCreatedAt());
                    baseMapper.insert(item);
                    ServiceItemVO result = TicketViews.of(item);
                    events.append(
                            actor,
                            item.getId(),
                            null,
                            form.getClientActionId(),
                            hash,
                            "SERVICE_CREATED",
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
    public ServiceItemVO updateItem(IdentityDTO actor, ServiceItemUpdateForm form) {
        form.setServiceName(form.getServiceName().strip());
        form.setSupportScope(form.getSupportScope().strip());
        return mutate(
                actor,
                form.getId(),
                form.getRowVersion(),
                form.getClientActionId(),
                "SERVICE_UPDATED",
                form,
                form.getReason(),
                item -> {
                    if (!"ACTIVE".equals(item.getStatus()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    item.setServiceName(form.getServiceName());
                    item.setSupportScope(form.getSupportScope());
                    item.setOnsiteSupported(form.getOnsiteSupported());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceItemVO disable(IdentityDTO actor, ServiceStatusForm form) {
        return mutate(
                actor,
                form.getId(),
                form.getRowVersion(),
                form.getClientActionId(),
                "SERVICE_DISABLED",
                form,
                form.getReason(),
                item -> item.setStatus("DISABLED"));
    }

    /**
     * 串行化当前项目，先回执后版本，事件与变更原子提交。
     */
    private ServiceItemVO mutate(
            IdentityDTO actor,
            String id,
            long version,
            String key,
            String type,
            Object form,
            String reason,
            Consumer<ServiceItemEntity> change) {
        SupportAuthorization.administrator(actor);
        String hash = digest.hash(type, id, form);
        return transactions.execute(
                () -> {
                    ServiceItemVO receipt = receipt(actor, key, hash);
                    if (receipt != null) return receipt;
                    ServiceItemEntity item = baseMapper.lock(id);
                    if (item == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    receipt = receipt(actor, key, hash);
                    if (receipt != null) return receipt;
                    if (item.getRowVersion() != version)
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    ServiceItemVO before = TicketViews.of(item);
                    change.accept(item);
                    item.setUpdatedBy(actor.getOwnerId());
                    item.setChangeReason(reason);
                    item.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                    item.setRowVersion(version + 1);
                    if (baseMapper.update(
                            item,
                            new LambdaUpdateWrapper<ServiceItemEntity>()
                                    .eq(ServiceItemEntity::getId, id)
                                    .eq(ServiceItemEntity::getRowVersion, version))
                            != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
                    ServiceItemVO after = TicketViews.of(item);
                    events.append(actor, id, null, key, hash, type, before, after, reason);
                    return after;
                },
                () -> receipt(actor, key, hash));
    }

    /**
     * 回执返回当前有权目标，历史事件保留当时值。
     */
    private ServiceItemVO receipt(IdentityDTO actor, String key, String hash) {
        ServiceEventEntity event = events.receipt(actor, key, hash);
        return event == null ? null : query(actor, event.getServiceItemId());
    }
}
