package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.DeviceVO;
import com.yangaobo.servicedesk.ticket.model.vo.device.ProductModelVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.EntitledServiceVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketDetailVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IDeviceService;
import com.yangaobo.servicedesk.ticket.service.IProductModelService;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.ITicketService;

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
 * 客户支持范围的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class SupportController {
    private final IServiceEntitlementService entitlementService;
    private final IDeviceService deviceService;
    private final IProductModelService modelService;
    private final ITicketService ticketService;
    private final CurrentActor currentActor;
    private final com.yangaobo.servicedesk.ticket.service.IServiceItemService serviceItemService;

    /** 知识管理员只读目录用于选择适用规则，不获得客户设备或工单正文。 */
    @GetMapping("/knowledge-service-items")
    @io.swagger.v3.oas.annotations.Operation(summary = "查询知识规则可选服务目录")
    public Result<PageVO<com.yangaobo.servicedesk.ticket.model.vo.support.ServiceItemVO>> knowledgeItems(@Valid SupportQueryForm form) {
        var actor=currentActor.requireIdentity();com.yangaobo.servicedesk.ticket.service.SupportAuthorization.role(actor,"KNOWLEDGE_ADMIN");
        PageVO<com.yangaobo.servicedesk.ticket.model.vo.support.ServiceItemVO> data=serviceItemService.listItems(actor,form);return Result.success(data);
    }

    /** 返回当前客户有效服务项目；技术人员须指定已授权客户。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "返回当前客户有效服务项目；技术人员须指定已授权客户。")
    @GetMapping("/service-items")
    public Result<PageVO<EntitledServiceVO>> serviceItems(@Valid SupportQueryForm form) {
        return Result.success(entitlementService.available(currentActor.requireIdentity(), form));
    }

    /** 返回当前身份有权设备，退役记录可读但不可新关联。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "返回当前身份有权设备，退役记录可读但不可新关联。")
    @GetMapping("/devices")
    public Result<PageVO<DeviceVO>> devices(@Valid SupportQueryForm form) {
        return Result.success(deviceService.listResources(currentActor.requireIdentity(), form));
    }

    /** 返回公开可选型号目录。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "返回公开可选型号目录。")
    @GetMapping("/product-models")
    public Result<PageVO<ProductModelVO>> models(@Valid SupportQueryForm form) {
        return Result.success(modelService.listResources(currentActor.requireIdentity(), form));
    }

    /** 创建直接工单，AI草稿采用仍经同一服务用例。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "创建直接工单，AI草稿采用仍经同一服务用例。")
    @PostMapping
    public ResponseEntity<Result<TicketVO>> create(@Valid @RequestBody TicketCreateForm form) {
        TicketVO data = ticketService.create(currentActor.requireIdentity(), form);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(data));
    }

    /** 原请求键回执先于新资格检查。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "原请求键回执先于新资格检查。")
    @GetMapping("/by-request/{clientRequestId}")
    public Result<TicketVO> byRequest(@PathVariable String clientRequestId) {
        return Result.success(
                ticketService.byRequest(currentActor.requireIdentity(), clientRequestId));
    }

    /** 本人或当前服务范围列表。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "本人或当前服务范围列表。")
    @GetMapping
    public Result<PageVO<TicketVO>> list(@Valid TicketQueryForm form) {
        return Result.success(ticketService.list(currentActor.requireIdentity(), form));
    }

    /** 当前权限工单详情和首批时间线。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "当前权限工单详情和首批时间线。")
    @GetMapping("/{id}")
    public Result<TicketDetailVO> query(@PathVariable String id) {
        return Result.success(ticketService.query(currentActor.requireIdentity(), id));
    }
}
