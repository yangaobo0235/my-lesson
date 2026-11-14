package com.yangaobo.servicedesk.knowledge.controller.admin;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.knowledge.security.CurrentActor;
import com.yangaobo.servicedesk.knowledge.service.IDocumentSeriesService;
import com.yangaobo.servicedesk.knowledge.model.form.SeriesCreateForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentSeriesVO;

/**
 * 知识来源系列管理。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/document-series")
@lombok.RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "知识来源系列管理")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentSeriesAdminController {
    private final CurrentActor currentActor;
    private final IDocumentSeriesService documentSeriesService;
    /** 按修订关联身份读取系列详情。 */
    @PostMapping("/query")
    @io.swagger.v3.oas.annotations.Operation(summary = "读取资料系列")
    public Result<DocumentSeriesVO> query(@Valid @RequestBody IdForm form) {
        return Result.success(documentSeriesService.query(currentActor.requireIdentity(),form.getId()));
    }
    /** 分页选择资料系列。 */
    @PostMapping("/list")
    @io.swagger.v3.oas.annotations.Operation(summary = "分页选择资料系列")
    public Result<PageVO<DocumentSeriesVO>> list(@Valid @RequestBody BasePageForm form) {
        PageVO<DocumentSeriesVO> data=documentSeriesService.list(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 登记固定来源受众系列。 */
    @PostMapping("/add")
    @io.swagger.v3.oas.annotations.Operation(summary = "登记固定来源受众系列")
    public Result<DocumentSeriesVO> add(@Valid @RequestBody SeriesCreateForm form) {
        DocumentSeriesVO data=documentSeriesService.add(currentActor.requireIdentity(),form);return Result.success(data);
    }

}
