package com.yangaobo.servicedesk.ticket.service;

/**
 * 工单期限收敛的业务边界。
 *
 * @author yangaobo
 */
public interface ITicketDeadlineService {
    /** 扫描有界到期候选，每笔在独立短事务重核；返回实际处理数量。 */
    int scan(int limit);
}
