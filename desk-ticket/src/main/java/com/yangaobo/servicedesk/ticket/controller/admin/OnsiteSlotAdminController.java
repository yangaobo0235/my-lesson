package com.yangaobo.servicedesk.ticket.controller.admin;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotUpdateForm;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IOnsiteSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人工核对出勤后的区域时段管理。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/onsite-slot")
@RequiredArgsConstructor
@Tag(name = "区域上门时段")
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class OnsiteSlotAdminController {
    private final IOnsiteSlotService onsiteSlotService;
    private final CurrentActor currentActor;

    /**
     * 查询时段详情，编辑使用本次读取的原版本。
     */
    @PostMapping("/query")
    @Operation(summary = "查询时段详情")
    public Result<OnsiteSlotVO> query(@Valid @RequestBody IdForm form) {
        return Result.success(
                onsiteSlotService.query(currentActor.requireIdentity(), form.getId()));
    }

    /**
     * 修改尚未发布的时段。
     */
    @PostMapping("/update")
    @Operation(summary = "修改草稿时段")
    public Result<OnsiteSlotVO> update(@Valid @RequestBody OnsiteSlotUpdateForm form) {
        return Result.success(onsiteSlotService.update(currentActor.requireIdentity(), form));
    }

    /**
     * 分页查看管理员时段。
     */
    @PostMapping("/list")
    @Operation(summary = "分页查看区域时段")
    public Result<PageVO<OnsiteSlotVO>> list(@Valid @RequestBody BasePageForm form) {
        PageVO<OnsiteSlotVO> data = onsiteSlotService.list(currentActor.requireIdentity(), form);
        return Result.success(data);
    }

    /**
     * 建立出勤容量已核对的草稿窗口。
     */
    @PostMapping("/add")
    @Operation(summary = "建立草稿时段")
    public ResponseEntity<Result<OnsiteSlotVO>> add(@Valid @RequestBody OnsiteSlotForm form) {
        OnsiteSlotVO data = onsiteSlotService.add(currentActor.requireIdentity(), form);
        return ResponseEntity.status(201).body(Result.success(data));
    }

    /**
     * 发布并冻结出勤容量与窗口。
     */
    @PostMapping("/publish")
    @Operation(summary = "发布时段")
    public Result<OnsiteSlotVO> publish(@Valid @RequestBody IdVersionForm form) {
        OnsiteSlotVO data = onsiteSlotService.change(currentActor.requireIdentity(), form, false);
        return Result.success(data);
    }

    /**
     * 关停新申请并保留原履约。
     */
    @PostMapping("/close")
    @Operation(summary = "关停新申请")
    public Result<OnsiteSlotVO> close(@Valid @RequestBody IdVersionForm form) {
        OnsiteSlotVO data = onsiteSlotService.change(currentActor.requireIdentity(), form, true);
        return Result.success(data);
    }
}
