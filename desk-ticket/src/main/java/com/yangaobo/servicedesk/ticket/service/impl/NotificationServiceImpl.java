package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.mapper.NotificationMapper;
import com.yangaobo.servicedesk.ticket.mapper.NotificationQueryMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.NotificationEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.enums.VisibilityEnum;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.NotificationVO;
import com.yangaobo.servicedesk.ticket.service.INotificationService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import com.yangaobo.servicedesk.ticket.service.TicketScope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内通知的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, NotificationEntity>
        implements INotificationService {
    private final NotificationQueryMapper queryMapper;
    private final TicketMapper ticketMapper;
    private final TicketEventMapper eventMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<NotificationVO> list(IdentityDTO actor, BasePageForm form) {
        TicketScope.requireReader(actor);
        boolean customer = actor.getRoles().contains("CUSTOMER_USER");

        long pageTotal = queryMapper.count(actor, customer);
        var records = queryMapper.list(actor, customer, form.getPageSize(), form.offset());
        return PageVO.of(records, pageTotal, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void read(IdentityDTO actor, String id) {
        NotificationEntity notification = baseMapper.selectById(id);
        if (notification == null || !actor.getOwnerId().equals(notification.getRecipientId()))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        TicketEntity ticket = ticketMapper.selectById(notification.getTicketId());
        TicketEventEntity event = eventMapper.selectById(notification.getEventId());
        if (ticket == null
                || !SupportAuthorization.canRead(actor, ticket)
                || actor.getRoles().contains("CUSTOMER_USER")
                && !VisibilityEnum.PUBLIC.getValue().equals(event.getVisibility()))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        baseMapper.update(
                null,
                new LambdaUpdateWrapper<NotificationEntity>()
                        .eq(NotificationEntity::getId, id)
                        .isNull(NotificationEntity::getReadAt)
                        .set(NotificationEntity::getReadAt, ticketMapper.decisionTime()));
    }
}
