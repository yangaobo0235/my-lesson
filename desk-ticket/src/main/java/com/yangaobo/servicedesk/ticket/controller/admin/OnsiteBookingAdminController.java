package com.yangaobo.servicedesk.ticket.controller.admin;

import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteBookingVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IOnsiteBookingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 现场履约核对沿原预约读取，不提供异步重投操作。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/onsite-booking")
@lombok.RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "现场预约核对")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class OnsiteBookingAdminController {
    /** 预约与容量权威用例。 */
    private final IOnsiteBookingService onsiteBookingService;
    /** 每请求复核当前身份。 */
    private final CurrentActor currentActor;
    /** 工单权限不由管理员菜单或预约标识授予。 */
    @PostMapping("/query")
    @io.swagger.v3.oas.annotations.Operation(summary = "按原预约查询履约回执")
    public Result<OnsiteBookingVO> query(@Valid @RequestBody IdForm form) {
        return Result.success(onsiteBookingService.query(currentActor.requireIdentity(),form.getId()));
    }
}
