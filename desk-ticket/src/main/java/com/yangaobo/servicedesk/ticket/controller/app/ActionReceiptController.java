package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.validator.Identifier;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IActionReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前拥有者核对正式动作结果，依赖AI故障不影响回执读取。
 *
 * @author yangaobo
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/tickets/action-receipts")
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ActionReceiptController {
    private final IActionReceiptService receiptService;
    private final CurrentActor currentActor;

    /**
     * 原动作键只在当前账号内查找。
     */
    @GetMapping("/by-action/{key}")
    public Result<TicketActionReceiptVO> byAction(@PathVariable @Identifier String key) {
        TicketActionReceiptVO data = receiptService.byAction(currentActor.requireIdentity(), key);
        return Result.success(data);
    }

    /**
     * 草稿根唯一采用结果仍须核验原操作者及当前工单访问权。
     */
    @GetMapping("/by-draft/{root}")
    public Result<TicketActionReceiptVO> byDraft(@PathVariable @Identifier String root) {
        TicketActionReceiptVO data = receiptService.byDraft(currentActor.requireIdentity(), root);
        return Result.success(data);
    }
}
