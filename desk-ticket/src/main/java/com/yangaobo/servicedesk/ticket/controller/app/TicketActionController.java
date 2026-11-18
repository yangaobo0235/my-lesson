package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketAssignmentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketCommentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketWaitForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketInformationForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContextForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketPriorityForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketResolveForm;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketEventVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.ITicketService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单幂等动作的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/tickets/{id}")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketActionController {
    private final ITicketService ticketService;
    private final CurrentActor currentActor;

    /**
     * 执行工单context，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单context，当前权限、原键及版本由业务层校验。")
    @PostMapping("/context")
    public Result<TicketVO> context(
            @PathVariable String id, @Valid @RequestBody TicketContextForm form) {
        TicketVO data = ticketService.updateContext(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单accept，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单accept，当前权限、原键及版本由业务层校验。")
    @PostMapping("/accept")
    public Result<TicketActionReceiptVO> accept(
            @PathVariable String id, @Valid @RequestBody TicketActionForm form) {
        TicketActionReceiptVO data = ticketService.accept(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单assign，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单assign，当前权限、原键及版本由业务层校验。")
    @PostMapping("/assign")
    public Result<TicketActionReceiptVO> assign(
            @PathVariable String id, @Valid @RequestBody TicketAssignmentForm form) {
        TicketActionReceiptVO data =
                ticketService.assign(currentActor.requireIdentity(), id, form, false);
        return Result.success(data);
    }

    /**
     * 执行工单reassign，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单reassign，当前权限、原键及版本由业务层校验。")
    @PostMapping("/reassign")
    public Result<TicketActionReceiptVO> reassign(
            @PathVariable String id, @Valid @RequestBody TicketAssignmentForm form) {
        TicketActionReceiptVO data =
                ticketService.assign(currentActor.requireIdentity(), id, form, true);
        return Result.success(data);
    }

    /**
     * 执行工单priority，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单priority，当前权限、原键及版本由业务层校验。")
    @PostMapping("/priority")
    public Result<TicketActionReceiptVO> priority(
            @PathVariable String id, @Valid @RequestBody TicketPriorityForm form) {
        TicketActionReceiptVO data =
                ticketService.priority(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单request-information，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单request-information，当前权限、原键及版本由业务层校验。")
    @PostMapping("/request-information")
    public Result<TicketActionReceiptVO> requestInformation(
            @PathVariable String id, @Valid @RequestBody TicketInformationForm form) {
        TicketActionReceiptVO data =
                ticketService.requestInformation(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 登记外部等待，动作键和行版本由业务用例核验。
     */
    @PostMapping("/wait-external")
    @Operation(summary = "登记外部等待")
    public Result<TicketActionReceiptVO> waitExternal(
            @PathVariable String id, @Valid @RequestBody TicketWaitForm form) {
        TicketActionReceiptVO data = ticketService.waitExternal(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 结束当前等待，动作键和行版本由业务用例核验。
     */
    @PostMapping("/end-wait")
    @Operation(summary = "结束当前等待")
    public Result<TicketActionReceiptVO> endWait(
            @PathVariable String id, @Valid @RequestBody TicketContentForm form) {
        TicketActionReceiptVO data = ticketService.endWait(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 记录跟进并设置下一期限，动作键和行版本由业务用例核验。
     */
    @PostMapping("/follow-up")
    @Operation(summary = "记录跟进并设置下一期限")
    public Result<TicketActionReceiptVO> followUp(
            @PathVariable String id, @Valid @RequestBody TicketWaitForm form) {
        TicketActionReceiptVO data = ticketService.followUp(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单supplements，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单supplements，当前权限、原键及版本由业务层校验。")
    @PostMapping("/supplements")
    public Result<TicketActionReceiptVO> supplements(
            @PathVariable String id, @Valid @RequestBody TicketContentForm form) {
        TicketActionReceiptVO data =
                ticketService.supplement(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单comments，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单comments，当前权限、原键及版本由业务层校验。")
    @PostMapping("/comments")
    public Result<TicketActionReceiptVO> comments(
            @PathVariable String id, @Valid @RequestBody TicketCommentForm form) {
        TicketActionReceiptVO data =
                ticketService.comment(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单resolve，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单resolve，当前权限、原键及版本由业务层校验。")
    @PostMapping("/resolve")
    public Result<TicketActionReceiptVO> resolve(
            @PathVariable String id, @Valid @RequestBody TicketResolveForm form) {
        TicketActionReceiptVO data =
                ticketService.resolve(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单confirm，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单confirm，当前权限、原键及版本由业务层校验。")
    @PostMapping("/confirm")
    public Result<TicketActionReceiptVO> confirm(
            @PathVariable String id, @Valid @RequestBody TicketActionForm form) {
        TicketActionReceiptVO data =
                ticketService.confirm(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单unresolved，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单unresolved，当前权限、原键及版本由业务层校验。")
    @PostMapping("/unresolved")
    public Result<TicketActionReceiptVO> unresolved(
            @PathVariable String id, @Valid @RequestBody TicketContentForm form) {
        TicketActionReceiptVO data =
                ticketService.unresolved(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 执行工单cancel，当前权限、原键及版本由业务层校验。
     */
    @Operation(summary = "执行工单cancel，当前权限、原键及版本由业务层校验。")
    @PostMapping("/cancel")
    public Result<TicketActionReceiptVO> cancel(
            @PathVariable String id, @Valid @RequestBody TicketContentForm form) {
        TicketActionReceiptVO data = ticketService.cancel(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 查询原动作回执，网络未知时不创建新动作键。
     */
    @Operation(summary = "查询原动作回执，网络未知时不创建新动作键。")
    @GetMapping("/actions/{key}")
    public Result<TicketActionReceiptVO> receipt(
            @PathVariable String id, @PathVariable String key) {
        TicketActionReceiptVO data =
                ticketService.actionReceipt(currentActor.requireIdentity(), id, key);
        return Result.success(data);
    }

    /**
     * 分页读取当前可见时间线。
     */
    @Operation(summary = "分页读取当前可见时间线。")
    @GetMapping("/events")
    public Result<PageVO<TicketEventVO>> events(@PathVariable String id, @Valid BasePageForm form) {
        PageVO<TicketEventVO> data = ticketService.events(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }
}
