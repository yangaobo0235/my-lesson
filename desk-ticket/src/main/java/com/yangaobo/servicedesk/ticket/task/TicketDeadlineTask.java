package com.yangaobo.servicedesk.ticket.task;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.yangaobo.servicedesk.ticket.service.ITicketDeadlineService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 工单期限收敛的业务边界。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketDeadlineTask {
    private final ITicketDeadlineService ticketDeadlineService;

    /** XXL只触发有界业务扫描，重复触发由数据库状态收敛。 */
    @XxlJob("ticketDeadlineScan")
    public void execute() {
        String parameter = XxlJobHelper.getJobParam();
        int limit = parameter == null || parameter.isBlank() ? 100 : Integer.parseInt(parameter);
        int completed = ticketDeadlineService.scan(limit);
        XxlJobHelper.handleSuccess("已收敛工单数：" + completed);
    }
}
