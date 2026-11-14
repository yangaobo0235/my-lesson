package com.yangaobo.servicedesk.user.controller.admin;

import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.user.model.form.account.AuditQueryForm;
import com.yangaobo.servicedesk.user.model.vo.account.AuditEventVO;
import com.yangaobo.servicedesk.user.security.CurrentActor;
import com.yangaobo.servicedesk.user.service.IAdminAuditService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份管理审计的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/audit-event")
@RequiredArgsConstructor
@Tag(name = "身份管理审计")
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class AuditEventAdminController {
    private final IAdminAuditService adminAuditService;
    private final CurrentActor currentActor;

    /** 查询授权后的管理变更历史。 */
    @io.swagger.v3.oas.annotations.Operation(summary = "查询授权后的管理变更历史。")
    @PostMapping("/list")
    public Result<PageVO<AuditEventVO>> list(@Valid @RequestBody AuditQueryForm form) {
        PageVO<AuditEventVO> data =
                adminAuditService.list(currentActor.requireBusinessIdentity(), form);
        return Result.success(data);
    }
}
