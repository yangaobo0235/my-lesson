package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 在工单事务中统一推进工单写版本与更新时间。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketStateWriter {
    private final TicketMapper ticketMapper;

    /**
     * 调用者持有工单锁，条件更新失败时回滚本次业务动作。
     *
     * @param ticket 当前持锁工单
     * @param now    数据库决策时刻
     */
    public void save(TicketEntity ticket, LocalDateTime now) {
        long version = ticket.getRowVersion();
        ticket.setRowVersion(version + 1);
        ticket.setUpdatedAt(now);
        if (ticketMapper.update(ticket, new LambdaUpdateWrapper<TicketEntity>()
                .eq(TicketEntity::getId, ticket.getId())
                .eq(TicketEntity::getRowVersion, version)) != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
    }
}
