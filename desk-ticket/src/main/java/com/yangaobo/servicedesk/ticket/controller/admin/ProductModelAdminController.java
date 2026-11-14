package com.yangaobo.servicedesk.ticket.controller.admin;

import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.device.ProductModelCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.device.ProductModelUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.ProductModelVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IProductModelService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产品型号的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/product-model")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ProductModelAdminController {
    private final IProductModelService businessService;
    private final CurrentActor currentActor;

    /** 执行product-model的list动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行product-model的list动作，资源授权由服务层逐次核对。")
    @PostMapping("/list")
    public Result<PageVO<ProductModelVO>> list(@Valid @RequestBody SupportQueryForm form) {
        PageVO<ProductModelVO> data =
                businessService.listResources(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行product-model的query动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行product-model的query动作，资源授权由服务层逐次核对。")
    @PostMapping("/query")
    public Result<ProductModelVO> query(@Valid @RequestBody IdForm form) {
        ProductModelVO data = businessService.query(currentActor.requireIdentity(), form.getId());
        return Result.success(data);
    }

    /** 执行product-model的add动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行product-model的add动作，资源授权由服务层逐次核对。")
    @PostMapping("/add")
    public Result<ProductModelVO> add(@Valid @RequestBody ProductModelCreateForm form) {
        ProductModelVO data = businessService.create(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行product-model的update动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行product-model的update动作，资源授权由服务层逐次核对。")
    @PostMapping("/update")
    public Result<ProductModelVO> update(@Valid @RequestBody ProductModelUpdateForm form) {
        ProductModelVO data = businessService.updateResource(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /** 执行product-model的disable动作，资源授权由服务层逐次核对。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行product-model的disable动作，资源授权由服务层逐次核对。")
    @PostMapping("/disable")
    public Result<ProductModelVO> disable(@Valid @RequestBody IdVersionForm form) {
        ProductModelVO data = businessService.disable(currentActor.requireIdentity(), form);
        return Result.success(data);
    }
}
