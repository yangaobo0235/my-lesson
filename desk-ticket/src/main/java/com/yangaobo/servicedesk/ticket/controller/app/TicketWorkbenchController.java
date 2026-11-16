package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketOverviewVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.ITicketOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketWorkbenchController {
    private final ITicketOverviewService overviewService;
    private final CurrentActor currentActor;

    /**
     * 当前身份的安全概况，分别显示确认和超时结单。
     */
    @Operation(summary = "当前身份的安全概况，分别显示确认和超时结单。")
    @GetMapping("/overview")
    public Result<TicketOverviewVO> overview() {
        TicketOverviewVO data = overviewService.overview(currentActor.requireIdentity());
        return Result.success(data);
    }
}
