package com.yangaobo.servicedesk.ticket.controller.admin;

import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.support.EntitlementGrantForm;
import com.yangaobo.servicedesk.ticket.model.form.support.EntitlementUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceEventsForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceStatusForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEntitlementVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEventVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.IServiceEventService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户服务资格的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/service-entitlement")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceEntitlementAdminController {
    private final IServiceEntitlementService businessService;
    private final CurrentActor currentActor;
    private final IServiceEventService eventService;

    /**
     * 执行service-entitlement的list动作，资源授权由服务层逐次核对。
     */
    @Operation(summary = "执行service-entitlement的list动作，资源授权由服务层逐次核对。")
    @PostMapping("/list")
    public Result<PageVO<ServiceEntitlementVO>> list(@Valid @RequestBody SupportQueryForm form) {
        PageVO<ServiceEntitlementVO> data =
                businessService.listEntitlements(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /**
     * 执行service-entitlement的query动作，资源授权由服务层逐次核对。
     */
    @Operation(summary = "执行service-entitlement的query动作，资源授权由服务层逐次核对。")
    @PostMapping("/query")
    public Result<ServiceEntitlementVO> query(@Valid @RequestBody IdForm form) {
        ServiceEntitlementVO data =
                businessService.query(currentActor.requireIdentity(), form.getId());
        return Result.success(data);
    }

    /**
     * 执行service-entitlement的grant动作，资源授权由服务层逐次核对。
     */
    @Operation(summary = "执行service-entitlement的grant动作，资源授权由服务层逐次核对。")
    @PostMapping("/grant")
    public ResponseEntity<Result<ServiceEntitlementVO>> grant(
            @Valid @RequestBody EntitlementGrantForm form) {
        ServiceEntitlementVO data = businessService.grant(currentActor.requireIdentity(), form);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(data));
    }

    /**
     * 执行service-entitlement的update动作，资源授权由服务层逐次核对。
     */
    @Operation(summary = "执行service-entitlement的update动作，资源授权由服务层逐次核对。")
    @PostMapping("/update")
    public Result<ServiceEntitlementVO> update(@Valid @RequestBody EntitlementUpdateForm form) {
        ServiceEntitlementVO data =
                businessService.updateEntitlement(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /**
     * 执行service-entitlement的suspend动作，资源授权由服务层逐次核对。
     */
    @Operation(summary = "执行service-entitlement的suspend动作，资源授权由服务层逐次核对。")
    @PostMapping("/suspend")
    public Result<ServiceEntitlementVO> suspend(@Valid @RequestBody ServiceStatusForm form) {
        ServiceEntitlementVO data =
                businessService.setSuspended(currentActor.requireIdentity(), form, true);
        return Result.success(data);
    }

    /**
     * 执行service-entitlement的resume动作，资源授权由服务层逐次核对。
     */
    @Operation(summary = "执行service-entitlement的resume动作，资源授权由服务层逐次核对。")
    @PostMapping("/resume")
    public Result<ServiceEntitlementVO> resume(@Valid @RequestBody ServiceStatusForm form) {
        ServiceEntitlementVO data =
                businessService.setSuspended(currentActor.requireIdentity(), form, false);
        return Result.success(data);
    }

    /**
     * 按当前管理权限分页读取不可变变更历史。
     */
    @Operation(summary = "按当前管理权限分页读取不可变变更历史。")
    @PostMapping("/events")
    public Result<PageVO<ServiceEventVO>> events(@Valid @RequestBody ServiceEventsForm form) {
        return Result.success(eventService.events(currentActor.requireIdentity(), form, true));
    }
}
