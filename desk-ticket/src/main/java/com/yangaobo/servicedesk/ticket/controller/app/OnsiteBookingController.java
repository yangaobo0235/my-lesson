package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.dto.onsite.OnsiteBookingResult;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteBookingForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteFinishForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.OnsiteEligibilityForm;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteBookingVO;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IOnsiteBookingService;
import com.yangaobo.servicedesk.ticket.service.ITicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单现场资格、区域时段、原键预约及真实履约入口。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/tickets/{id}")
@RequiredArgsConstructor
@Tag(name = "工单上门服务")
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class OnsiteBookingController {
    private final IOnsiteBookingService onsiteBookingService;
    private final ITicketService ticketService;
    private final CurrentActor currentActor;

    /**
     * 现场资格只由客服或当前处理人确认。
     */
    @PostMapping("/onsite-eligibility")
    @Operation(summary = "核对现场资格")
    public Result<TicketActionReceiptVO> eligibility(
            @PathVariable String id, @Valid @RequestBody OnsiteEligibilityForm form) {
        TicketActionReceiptVO data =
                ticketService.onsiteEligibility(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 展示量不保证提交时仍有容量。
     */
    @GetMapping("/onsite-slots")
    @Operation(summary = "查询区域开放时段")
    public Result<PageVO<OnsiteSlotVO>> slots(@PathVariable String id, @Valid BasePageForm form) {
        PageVO<OnsiteSlotVO> data =
                onsiteBookingService.slots(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 新申请与确定结果一并提交，原键重放保留真实状态。
     */
    @ApiResponse(responseCode = "200", description = "原请求预约回执", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "201", description = "预约已创建", useReturnTypeSchema = true)
    @PostMapping("/onsite-bookings")
    @Operation(summary = "按原键预约")
    public ResponseEntity<Result<OnsiteBookingVO>> book(
            @PathVariable String id, @Valid @RequestBody OnsiteBookingForm form) {
        OnsiteBookingResult result =
                onsiteBookingService.book(currentActor.requireIdentity(), id, form);
        return ResponseEntity.status(result.isCreated() ? 201 : 200)
                .body(Result.success(result.getBooking()));
    }

    /**
     * 当前有权工单的分页历史。
     */
    @GetMapping("/onsite-bookings")
    @Operation(summary = "查询预约历史")
    public Result<PageVO<OnsiteBookingVO>> history(
            @PathVariable String id, @Valid BasePageForm form) {
        PageVO<OnsiteBookingVO> data =
                onsiteBookingService.history(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 结果未知沿原键核对，不换键。
     */
    @GetMapping("/onsite-bookings/by-request/{key}")
    @Operation(summary = "核对原预约请求")
    public Result<OnsiteBookingVO> receipt(@PathVariable String id, @PathVariable String key) {
        OnsiteBookingVO data =
                onsiteBookingService.byRequest(currentActor.requireIdentity(), id, key);
        return Result.success(data);
    }

    /**
     * 到访前取消只回补一次。
     */
    @PostMapping("/onsite-bookings/{bookingId}/cancel")
    @Operation(summary = "取消预约")
    public Result<OnsiteBookingVO> cancel(@PathVariable String id, @PathVariable String bookingId) {
        OnsiteBookingVO data =
                onsiteBookingService.end(currentActor.requireIdentity(), id, bookingId, null);
        return Result.success(data);
    }

    /**
     * 当前处理人登记真实结果。
     */
    @PostMapping("/onsite-bookings/{bookingId}/finish")
    @Operation(summary = "登记实际履约")
    public Result<OnsiteBookingVO> finish(
            @PathVariable String id,
            @PathVariable String bookingId,
            @Valid @RequestBody OnsiteFinishForm form) {
        OnsiteBookingVO data =
                onsiteBookingService.end(
                        currentActor.requireIdentity(), id, bookingId, form.getFinishResult());
        return Result.success(data);
    }
}
