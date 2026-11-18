package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketDetailVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.ITicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供正式工单创建、回执和授权查询入口。
 *
 * @author yangaobo
 */
@RestController
@Tag(name = "工单受理与查询")
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketController {
    private final ITicketService ticketService;
    private final CurrentActor currentActor;

    /**
     * 创建直接工单，AI草稿采用仍经同一服务用例。
     *
     * @param form 报修内容、服务范围及原请求键
     * @return 新建工单或原请求回执
     */
    @Operation(summary = "创建直接工单，AI草稿采用仍经同一服务用例。")
    @ApiResponse(responseCode = "201", description = "工单已创建或返回原请求回执", useReturnTypeSchema = true)
    @PostMapping
    public ResponseEntity<Result<TicketVO>> create(@Valid @RequestBody TicketCreateForm form) {
        TicketVO data = ticketService.create(currentActor.requireIdentity(), form);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(data));
    }

    /**
     * 原请求键回执先于新资格检查。
     *
     * @param clientRequestId 建单时使用的原请求键
     * @return 当前操作者有权读取的原工单
     */
    @Operation(summary = "原请求键回执先于新资格检查。")
    @GetMapping("/by-request/{clientRequestId}")
    public Result<TicketVO> byRequest(@PathVariable String clientRequestId) {
        TicketVO data = ticketService.byRequest(currentActor.requireIdentity(), clientRequestId);
        return Result.success(data);
    }

    /**
     * 本人或当前服务范围列表。
     *
     * @param form 工单筛选与分页参数
     * @return 当前授权范围内的工单分页
     */
    @Operation(summary = "本人或当前服务范围列表。")
    @GetMapping
    public Result<PageVO<TicketVO>> list(@Valid TicketQueryForm form) {
        PageVO<TicketVO> data = ticketService.list(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /**
     * 当前权限工单详情和首批时间线。
     *
     * @param id 正式工单标识
     * @return 授权后的工单详情和时间线
     */
    @Operation(summary = "当前权限工单详情和首批时间线。")
    @GetMapping("/{id}")
    public Result<TicketDetailVO> query(@PathVariable String id) {
        TicketDetailVO data = ticketService.query(currentActor.requireIdentity(), id);
        return Result.success(data);
    }
}
