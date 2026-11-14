package com.yangaobo.servicedesk.ticket.controller.admin;

import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceEventsForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceItemCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceItemUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceStatusForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEventVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceItemVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IServiceEventService;
import com.yangaobo.servicedesk.ticket.service.IServiceItemService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IT服务项目的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/service-item")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceItemAdminController {
    private final IServiceItemService businessService;
    private final CurrentActor currentActor;
    private final IServiceEventService eventService;

    /** 执行service-item的list动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行service-item的list动作，资源授权由服务层逐次核对。")
    @PostMapping("/list")
    public Result<PageVO<ServiceItemVO>> list(@Valid @RequestBody SupportQueryForm form) {
        PageVO<ServiceItemVO> data =
                businessService.listItems(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行service-item的query动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行service-item的query动作，资源授权由服务层逐次核对。")
    @PostMapping("/query")
    public Result<ServiceItemVO> query(@Valid @RequestBody IdForm form) {
        ServiceItemVO data = businessService.query(currentActor.requireIdentity(), form.getId());
        return Result.success(data);
    }

    /** 执行service-item的add动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行service-item的add动作，资源授权由服务层逐次核对。")
    @PostMapping("/add")
    public Result<ServiceItemVO> add(@Valid @RequestBody ServiceItemCreateForm form) {
        ServiceItemVO data = businessService.create(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行service-item的update动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行service-item的update动作，资源授权由服务层逐次核对。")
    @PostMapping("/update")
    public Result<ServiceItemVO> update(@Valid @RequestBody ServiceItemUpdateForm form) {
        ServiceItemVO data = businessService.updateItem(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行service-item的disable动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行service-item的disable动作，资源授权由服务层逐次核对。")
    @PostMapping("/disable")
    public Result<ServiceItemVO> disable(@Valid @RequestBody ServiceStatusForm form) {
        ServiceItemVO data = businessService.disable(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 按当前管理权限分页读取不可变变更历史。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "按当前管理权限分页读取不可变变更历史。")
    @PostMapping("/events")
    public Result<PageVO<ServiceEventVO>> events(@Valid @RequestBody ServiceEventsForm form) {
        return Result.success(eventService.events(currentActor.requireIdentity(), form, false));
    }
}
