package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.mapper.OnsiteSlotMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import com.yangaobo.servicedesk.ticket.model.enums.OnsiteSlotStatusEnum;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotUpdateForm;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;
import com.yangaobo.servicedesk.ticket.service.IOnsiteSlotService;
import com.yangaobo.servicedesk.ticket.service.OnsiteViews;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 管理固定时段；容量变更只由预约事务维护。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class OnsiteSlotServiceImpl extends ServiceImpl<OnsiteSlotMapper, OnsiteSlotEntity>
        implements IOnsiteSlotService {
    private final BusinessTransaction transactions;
    private final TicketMapper ticketMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteSlotVO query(IdentityDTO actor, String id) {
        SupportAuthorization.administrator(actor);
        var slot = baseMapper.selectById(id);
        if (slot == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return OnsiteViews.slot(slot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteSlotVO update(IdentityDTO actor, OnsiteSlotUpdateForm form) {
        SupportAuthorization.administrator(actor);
        return transactions.execute(
                () -> {
                    var slot = baseMapper.lock(form.getId());
                    if (slot == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    if (!slot.getRowVersion().equals(form.getRowVersion())
                            || !OnsiteSlotStatusEnum.DRAFT.getValue().equals(slot.getStatus()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    LocalDateTime starts =
                            form.getStartsAt()
                                    .withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDateTime();
                    LocalDateTime opens =
                            form.getBookingOpensAt()
                                    .withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDateTime();
                    LocalDateTime closes =
                            form.getBookingClosesAt()
                                    .withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDateTime();
                    validateWindow(starts, opens, closes);
                    LocalDateTime now = ticketMapper.decisionTime();
                    if (!now.isBefore(opens))
                        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    slot.setServiceAreaCode(form.getServiceAreaCode());
                    slot.setStartsAt(starts);
                    slot.setEndsAt(starts.plusHours(2));
                    slot.setBookingOpensAt(opens);
                    slot.setBookingClosesAt(closes);
                    slot.setCapacity(form.getCapacity());
                    slot.setRowVersion(slot.getRowVersion() + 1);
                    slot.setUpdatedAt(now);
                    baseMapper.updateById(slot);
                    return OnsiteViews.slot(slot);
                },
                () -> null);
    }

    /**
     * 固定北京时间窗口和开放区间由后端共同校验。
     */
    private void validateWindow(LocalDateTime starts, LocalDateTime opens, LocalDateTime closes) {
        if (!Set.of(1, 3, 6, 8).contains(starts.getHour())
                || starts.getMinute() != 0
                || starts.getSecond() != 0
                || starts.getNano() != 0
                || !opens.isBefore(closes)
                || closes.isAfter(starts))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<OnsiteSlotVO> list(IdentityDTO actor, BasePageForm form) {
        SupportAuthorization.administrator(actor);
        long total = baseMapper.countSlots();
        var records = baseMapper.listSlots(form.getPageSize(), form.offset()).stream().map(OnsiteViews::slot).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteSlotVO add(IdentityDTO actor, OnsiteSlotForm form) {
        SupportAuthorization.administrator(actor);
        LocalDateTime starts =
                form.getStartsAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime opens =
                form.getBookingOpensAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime closes =
                form.getBookingClosesAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        validateWindow(starts, opens, closes);
        return transactions.execute(
                () -> {
                    LocalDateTime now = ticketMapper.decisionTime();
                    if (!now.isBefore(opens))
                        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    OnsiteSlotEntity slot = new OnsiteSlotEntity();
                    slot.setId(IdentifierGenerator.next());
                    slot.setServiceAreaCode(form.getServiceAreaCode());
                    slot.setStartsAt(starts);
                    slot.setEndsAt(starts.plusHours(2));
                    slot.setBookingOpensAt(opens);
                    slot.setBookingClosesAt(closes);
                    slot.setCapacity(form.getCapacity());
                    slot.setReservedCount(0);
                    slot.setStatus(OnsiteSlotStatusEnum.DRAFT.getValue());
                    slot.setCreatedBy(actor.getOwnerId());
                    slot.setCreatedAt(now);
                    slot.setUpdatedAt(now);
                    slot.setRowVersion(0L);
                    baseMapper.insert(slot);
                    return OnsiteViews.slot(slot);
                },
                () -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OnsiteSlotVO change(IdentityDTO actor, IdVersionForm form, boolean close) {
        SupportAuthorization.administrator(actor);
        return transactions.execute(
                () -> {
                    OnsiteSlotEntity slot = baseMapper.lock(form.getId());
                    if (slot == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    if (!slot.getRowVersion().equals(form.getRowVersion())
                            || !(close
                            ? OnsiteSlotStatusEnum.PUBLISHED.getValue()
                            : OnsiteSlotStatusEnum.DRAFT.getValue())
                            .equals(slot.getStatus()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    LocalDateTime now = ticketMapper.decisionTime();
                    if (!close && now.isAfter(slot.getBookingOpensAt()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    if (close) {
                        slot.setStatus(OnsiteSlotStatusEnum.CLOSED.getValue());
                        slot.setClosedAt(now);
                        slot.setClosedBy(actor.getOwnerId());
                    } else {
                        slot.setStatus(OnsiteSlotStatusEnum.PUBLISHED.getValue());
                        slot.setPublishedAt(now);
                        slot.setPublishedBy(actor.getOwnerId());
                    }
                    slot.setRowVersion(slot.getRowVersion() + 1);
                    slot.setUpdatedAt(now);
                    baseMapper.updateById(slot);
                    return OnsiteViews.slot(slot);
                },
                () -> null);
    }
}
