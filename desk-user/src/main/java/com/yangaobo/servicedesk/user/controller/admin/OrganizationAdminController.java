package com.yangaobo.servicedesk.user.controller.admin;

import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;
import com.yangaobo.servicedesk.user.model.form.organization.OrganizationCreateForm;
import com.yangaobo.servicedesk.user.model.form.organization.OrganizationUpdateForm;
import com.yangaobo.servicedesk.user.model.vo.organization.OrganizationVO;
import com.yangaobo.servicedesk.user.security.CurrentActor;
import com.yangaobo.servicedesk.user.service.IOrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/organization")
@RequiredArgsConstructor
@Tag(name = "组织管理")
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class OrganizationAdminController {
    private final IOrganizationService organizationService;
    private final CurrentActor currentActor;

    /**
     * 执行organization的list用例，权限与事务由业务服务负责。
     */
    @Operation(summary = "执行organization的list用例，权限与事务由业务服务负责。")
    @PostMapping("/list")
    public Result<PageVO<OrganizationVO>> list(@Valid @RequestBody IdentityQueryForm form) {
        PageVO<OrganizationVO> data =
                organizationService.listOrganizations(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }

    /**
     * 执行organization的query用例，权限与事务由业务服务负责。
     */
    @Operation(summary = "执行organization的query用例，权限与事务由业务服务负责。")
    @PostMapping("/query")
    public Result<OrganizationVO> query(@Valid @RequestBody IdForm form) {
        OrganizationVO data =
                organizationService.query(currentActor.requireBusinessIdentity(), form.getId());
        return Result.success(data);
    }

    /**
     * 执行organization的add用例，权限与事务由业务服务负责。
     */
    @Operation(summary = "执行organization的add用例，权限与事务由业务服务负责。")
    @PostMapping("/add")
    public Result<OrganizationVO> add(@Valid @RequestBody OrganizationCreateForm form) {
        OrganizationVO data =
                organizationService.create(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }

    /**
     * 执行organization的update用例，权限与事务由业务服务负责。
     */
    @Operation(summary = "执行organization的update用例，权限与事务由业务服务负责。")
    @PostMapping("/update")
    public Result<OrganizationVO> update(@Valid @RequestBody OrganizationUpdateForm form) {
        OrganizationVO data =
                organizationService.updateOrganization(
                        currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }
}
