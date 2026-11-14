package com.yangaobo.servicedesk.user.controller.admin;

import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.user.model.form.account.AccountCreateForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountRolesForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountScopesForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountStatusForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountUpdateForm;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;
import com.yangaobo.servicedesk.user.model.form.auth.PasswordResetForm;
import com.yangaobo.servicedesk.user.model.vo.account.AccountVO;
import com.yangaobo.servicedesk.user.security.CurrentActor;
import com.yangaobo.servicedesk.user.service.IAccountService;

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
 * 账号的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/account")
@RequiredArgsConstructor
@Tag(name = "账号管理")
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class AccountAdminController {
    private final IAccountService accountService;
    private final CurrentActor currentActor;

    /** 执行account的list用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的list用例，权限与事务由业务服务负责。")
    @PostMapping("/list")
    public Result<PageVO<AccountVO>> list(@Valid @RequestBody IdentityQueryForm form) {
        PageVO<AccountVO> data =
                accountService.listAccounts(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }

    /** 执行account的query用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的query用例，权限与事务由业务服务负责。")
    @PostMapping("/query")
    public Result<AccountVO> query(@Valid @RequestBody IdForm form) {
        AccountVO data = accountService.query(currentActor.requireBusinessIdentity(), form.getId());
        return Result.success(data);
    }

    /** 执行account的add用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的add用例，权限与事务由业务服务负责。")
    @PostMapping("/add")
    public Result<AccountVO> add(@Valid @RequestBody AccountCreateForm form) {
        AccountVO data = accountService.create(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }

    /** 执行account的update用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的update用例，权限与事务由业务服务负责。")
    @PostMapping("/update")
    public Result<AccountVO> update(@Valid @RequestBody AccountUpdateForm form) {
        AccountVO data = accountService.updateAccount(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }

    /** 执行account的set-roles用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的set-roles用例，权限与事务由业务服务负责。")
    @PostMapping("/set-roles")
    public Result<AccountVO> setRoles(@Valid @RequestBody AccountRolesForm form) {
        AccountVO data = accountService.setRoles(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }

    /** 执行account的set-customer-scopes用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "执行account的set-customer-scopes用例，权限与事务由业务服务负责。")
    @PostMapping("/set-customer-scopes")
    public Result<AccountVO> setCustomerScopes(@Valid @RequestBody AccountScopesForm form) {
        AccountVO data = accountService.setScopes(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }

    /** 执行account的enable用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的enable用例，权限与事务由业务服务负责。")
    @PostMapping("/enable")
    public Result<AccountVO> enable(@Valid @RequestBody AccountStatusForm form) {
        AccountVO data =
                accountService.setStatus(currentActor.requireBusinessIdentity(), form, true);
        return Result.success(data);
    }

    /** 执行account的disable用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的disable用例，权限与事务由业务服务负责。")
    @PostMapping("/disable")
    public Result<AccountVO> disable(@Valid @RequestBody AccountStatusForm form) {
        AccountVO data =
                accountService.setStatus(currentActor.requireBusinessIdentity(), form, false);
        return Result.success(data);
    }

    /** 执行account的reset-password用例，权限与事务由业务服务负责。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "执行account的reset-password用例，权限与事务由业务服务负责。")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody PasswordResetForm form) {
        accountService.resetPassword(currentActor.requireBusinessIdentity(), form);
        return Result.success();
    }
}
