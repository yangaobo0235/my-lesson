package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.NotificationVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketOverviewVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.INotificationService;
import com.yangaobo.servicedesk.ticket.service.ITicketOverviewService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final INotificationService notificationService;
    private final CurrentActor currentActor;

    /** 当前身份的安全概况，分别显示确认和超时结单。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "当前身份的安全概况，分别显示确认和超时结单。")
    @GetMapping("/overview")
    public Result<TicketOverviewVO> overview() {
        TicketOverviewVO data = overviewService.overview(currentActor.requireIdentity());
        return Result.success(data);
    }

    /** 当前有权通知列表。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "当前有权通知列表。")
    @GetMapping("/notifications")
    public Result<PageVO<NotificationVO>> notifications(@Valid BasePageForm form) {
        PageVO<NotificationVO> data =
                notificationService.list(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 标记本人当前有权通知首次已读。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "标记本人当前有权通知首次已读。")
    @PostMapping("/notifications/{id}/read")
    public Result<Void> read(@PathVariable String id) {
        notificationService.read(currentActor.requireIdentity(), id);
        return Result.success();
    }
}
