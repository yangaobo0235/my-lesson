package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.DeviceVO;
import com.yangaobo.servicedesk.ticket.model.vo.device.ProductModelVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.EntitledServiceVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceItemVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IDeviceService;
import com.yangaobo.servicedesk.ticket.service.IProductModelService;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.IServiceItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供提单所需的服务项目、设备和可用型号查询。
 *
 * @author yangaobo
 */
@RestController
@Tag(name = "办公 IT 支持资源")
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class SupportResourcesController {
    private final IServiceEntitlementService entitlementService;
    private final IDeviceService deviceService;
    private final IProductModelService modelService;
    private final CurrentActor currentActor;
    private final IServiceItemService serviceItemService;

    /**
     * 知识管理员只读目录用于选择适用规则，不获得客户设备或工单正文。
     *
     * @param form 服务目录筛选与分页参数
     * @return 资料适用规则可选服务项目
     */
    @GetMapping("/knowledge-service-items")
    @Operation(summary = "查询知识规则可选服务目录")
    public Result<PageVO<ServiceItemVO>> knowledgeItems(@Valid SupportQueryForm form) {
        PageVO<ServiceItemVO> data = serviceItemService.listKnowledgeItems(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /**
     * 返回当前客户有效服务项目；技术人员须指定已授权客户。
     *
     * @param form 客户范围、筛选与分页参数
     * @return 当前客户有效服务项目分页
     */
    @Operation(summary = "返回当前客户有效服务项目；技术人员须指定已授权客户。")
    @GetMapping("/service-items")
    public Result<PageVO<EntitledServiceVO>> serviceItems(@Valid SupportQueryForm form) {
        PageVO<EntitledServiceVO> data = entitlementService.available(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /**
     * 返回当前身份有权设备，退役记录可读但不可新关联。
     *
     * @param form 客户范围、筛选与分页参数
     * @return 当前身份可读的设备分页
     */
    @Operation(summary = "返回当前身份有权设备，退役记录可读但不可新关联。")
    @GetMapping("/devices")
    public Result<PageVO<DeviceVO>> devices(@Valid SupportQueryForm form) {
        PageVO<DeviceVO> data = deviceService.listResources(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /**
     * 返回公开可选型号目录。
     *
     * @param form 型号筛选与分页参数
     * @return 可选型号分页
     */
    @Operation(summary = "返回公开可选型号目录。")
    @GetMapping("/product-models")
    public Result<PageVO<ProductModelVO>> models(@Valid SupportQueryForm form) {
        PageVO<ProductModelVO> data = modelService.listResources(currentActor.requireIdentity(), form);
        return Result.success(data);
    }
}
