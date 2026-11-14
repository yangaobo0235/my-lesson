package com.yangaobo.servicedesk.knowledge.controller.admin;

import com.yangaobo.servicedesk.knowledge.model.enums.IndexTransactionStateEnum;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.IdForm;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.knowledge.security.CurrentActor;
import com.yangaobo.servicedesk.knowledge.service.IIndexJobService;
import com.yangaobo.servicedesk.knowledge.model.form.IndexCreateForm;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeQueryForm;
import com.yangaobo.servicedesk.knowledge.model.vo.IndexJobVO;

/**
 * 索引事务提交及受控恢复。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/index-job")
@lombok.RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "索引事务提交及受控恢复")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class IndexJobAdminController {
    private final CurrentActor currentActor;
    private final IIndexJobService indexJobService;
    /** 只有本地已提交才报告202，准备或终止返回当前资源。 */
    @PostMapping("/add")
    @io.swagger.v3.oas.annotations.Operation(summary = "提交索引尝试")
    public org.springframework.http.ResponseEntity<Result<IndexJobVO>> add(@Valid @RequestBody IndexCreateForm form) {
        IndexJobVO data=indexJobService.add(currentActor.requireIdentity(),form);
        return org.springframework.http.ResponseEntity.status(IndexTransactionStateEnum.COMMITTED.getValue().equals(data.getSubmitState())?202:200).body(Result.success(data));
    }
    /** 查询原任务提交及AI执行事实。 */
    @PostMapping("/query")
    @io.swagger.v3.oas.annotations.Operation(summary = "查询原任务提交及AI执行事实")
    public Result<IndexJobVO> query(@Valid @RequestBody KnowledgeQueryForm form) {
        IndexJobVO data=indexJobService.query(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 按草稿版本提交取消指令。 */
    @PostMapping("/cancel")
    @io.swagger.v3.oas.annotations.Operation(summary = "按草稿版本提交取消指令")
    public Result<IndexJobVO> cancel(@Valid @RequestBody IdVersionForm form) {
        IndexJobVO data=indexJobService.cancel(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 审计后重放已提交原指令。 */
    @PostMapping("/replay-command")
    @io.swagger.v3.oas.annotations.Operation(summary = "审计后重放已提交原指令")
    public Result<IndexJobVO> replaycommand(@Valid @RequestBody IdForm form) {
        IndexJobVO data=indexJobService.replay(currentActor.requireIdentity(),form.getId());return Result.success(data);
    }

}
