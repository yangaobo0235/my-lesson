package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.mapper.ServiceEntitlementMapper;
import com.yangaobo.servicedesk.ticket.mapper.ServiceEventMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEntitlementEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEventEntity;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceEventsForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEventVO;
import com.yangaobo.servicedesk.ticket.service.IServiceEventService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import com.yangaobo.servicedesk.ticket.service.TicketViews;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 项目与资格变更审计的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceEventServiceImpl implements IServiceEventService {
    private final ServiceEventMapper serviceEventMapper;
    private final ServiceEntitlementMapper entitlementMapper;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public ServiceEventEntity receipt(IdentityDTO actor, String key, String hash) {
        ServiceEventEntity event =
                serviceEventMapper.selectOne(
                        new LambdaQueryWrapper<ServiceEventEntity>()
                                .eq(ServiceEventEntity::getActorId, actor.getOwnerId())
                                .eq(ServiceEventEntity::getClientActionId, key));
        if (event == null) return null;
        authorize(actor, event.getServiceItemId(), event.getEntitlementId());
        if (!event.getRequestHash().equals(hash))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return event;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(
            IdentityDTO actor,
            String service,
            String entitlement,
            String key,
            String hash,
            String type,
            Object before,
            Object after,
            String reason) {
        ServiceEventEntity event = new ServiceEventEntity();
        event.setId(UUID.randomUUID().toString().replace("-", ""));
        event.setServiceItemId(service);
        event.setEntitlementId(entitlement);
        event.setActorId(actor.getOwnerId());
        event.setClientActionId(key);
        event.setRequestHash(hash);
        event.setEventType(type);
        ObjectNode details = objectMapper.createObjectNode();
        details.put("schema_version", 1);
        details.put("reason", reason);
        details.set("before", objectMapper.valueToTree(before));
        details.set("after", objectMapper.valueToTree(after));
        event.setDetails(details);
        event.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        serviceEventMapper.insert(event);
    }

    /** {@inheritDoc} */
    @Override
    public PageVO<ServiceEventVO> events(
            IdentityDTO actor, ServiceEventsForm form, boolean entitlement) {
        authorize(actor, entitlement ? null : form.getId(), entitlement ? form.getId() : null);
        LambdaQueryWrapper<ServiceEventEntity> query = new LambdaQueryWrapper<ServiceEventEntity>();
        if (entitlement) query.eq(ServiceEventEntity::getEntitlementId, form.getId());
        else
            query.eq(ServiceEventEntity::getServiceItemId, form.getId())
                    .isNull(ServiceEventEntity::getEntitlementId);
        long total = serviceEventMapper.selectCount(query);
        query.orderByAsc(ServiceEventEntity::getCreatedAt, ServiceEventEntity::getId)
                .last(
                        "LIMIT "
                                + form.getPageSize()
                                + " OFFSET "
                                + ((long) (form.getPageNum() - 1) * form.getPageSize()));
        PageVO<ServiceEventVO> result = new PageVO<>();
        result.setTotal(total);
        result.setPageNum(form.getPageNum());
        result.setPageSize(form.getPageSize());
        result.setRecords(
                serviceEventMapper.selectList(query).stream().map(TicketViews::of).toList());
        return result;
    }

    /** 回执也必须按当前权限读取，撤权后不泄漏旧事件。 */
    private void authorize(IdentityDTO actor, String service, String entitlement) {
        if (entitlement == null) SupportAuthorization.administrator(actor);
        else {
            ServiceEntitlementEntity current = entitlementMapper.selectById(entitlement);
            if (current == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            SupportAuthorization.agent(actor, current.getCustomerId());
        }
    }
}
