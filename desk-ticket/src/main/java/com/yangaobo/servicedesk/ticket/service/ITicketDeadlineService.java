package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;

import java.time.LocalDateTime;

/**
 * 工单期限收敛的业务边界。
 *
 * @author yangaobo
 */
public interface ITicketDeadlineService {
    /**
     * 扫描有界到期候选，每笔在独立短事务重核；返回实际处理数量。
     */
    int scan(int limit);

    /**
     * 在接单、分派或期限扫描已取得的工单锁内补记首次受理超期。
     *
     * @param ticket 当前持锁工单，由调用者保存变更
     * @param now    数据库决策时刻
     * @return 是否首次登记超期
     */
    boolean recordAcceptanceOverdue(TicketEntity ticket, LocalDateTime now);
}
