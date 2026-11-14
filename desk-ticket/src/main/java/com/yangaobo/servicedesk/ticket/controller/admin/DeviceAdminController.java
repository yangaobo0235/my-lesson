package com.yangaobo.servicedesk.ticket.controller.admin;

import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.device.DeviceCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.device.DeviceUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.DeviceVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IDeviceService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户可选资产的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/device")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceAdminController {
    private final IDeviceService businessService;
    private final CurrentActor currentActor;

    /** 执行device的list动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行device的list动作，资源授权由服务层逐次核对。")
    @PostMapping("/list")
    public Result<PageVO<DeviceVO>> list(@Valid @RequestBody SupportQueryForm form) {
        PageVO<DeviceVO> data = businessService.listResources(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行device的query动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行device的query动作，资源授权由服务层逐次核对。")
    @PostMapping("/query")
    public Result<DeviceVO> query(@Valid @RequestBody IdForm form) {
        DeviceVO data = businessService.query(currentActor.requireIdentity(), form.getId());
        return Result.success(data);
    }

    /** 执行device的add动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行device的add动作，资源授权由服务层逐次核对。")
    @PostMapping("/add")
    public Result<DeviceVO> add(@Valid @RequestBody DeviceCreateForm form) {
        DeviceVO data = businessService.create(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行device的update动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行device的update动作，资源授权由服务层逐次核对。")
    @PostMapping("/update")
    public Result<DeviceVO> update(@Valid @RequestBody DeviceUpdateForm form) {
        DeviceVO data = businessService.updateResource(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行device的retire动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行device的retire动作，资源授权由服务层逐次核对。")
    @PostMapping("/retire")
    public Result<DeviceVO> retire(@Valid @RequestBody IdVersionForm form) {
        DeviceVO data = businessService.retire(currentActor.requireIdentity(), form);
        return Result.success(data);
    }
}
