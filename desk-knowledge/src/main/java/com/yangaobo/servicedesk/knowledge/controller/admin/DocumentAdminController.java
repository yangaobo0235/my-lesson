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
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.knowledge.security.CurrentActor;
import com.yangaobo.servicedesk.knowledge.service.IDocumentService;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeQueryForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentUploadForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentUpdateForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentPublishForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentVO;

/**
 * 知识原件修订及发布管理。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/document")
@lombok.RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "知识原件修订及发布管理")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentAdminController {
    private final CurrentActor currentActor;
    private final IDocumentService documentService;
    /** 分页查看资料修订。 */
    @PostMapping("/list")
    @io.swagger.v3.oas.annotations.Operation(summary = "分页查看资料修订")
    public Result<PageVO<DocumentVO>> list(@Valid @RequestBody KnowledgeQueryForm form) {
        PageVO<DocumentVO> data=documentService.list(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 查询修订详情与规则。 */
    @PostMapping("/query")
    @io.swagger.v3.oas.annotations.Operation(summary = "查询修订详情与规则")
    public Result<DocumentVO> query(@Valid @RequestBody KnowledgeQueryForm form) {
        DocumentVO data=documentService.query(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 沿当前上传者原键查询回执。 */
    @PostMapping("/query-upload")
    @io.swagger.v3.oas.annotations.Operation(summary = "沿当前上传者原键查询回执")
    public Result<DocumentVO> queryupload(@Valid @RequestBody KnowledgeQueryForm form) {
        DocumentVO data=documentService.query(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 修改草稿元数据及规则。 */
    @PostMapping("/update")
    @io.swagger.v3.oas.annotations.Operation(summary = "修改草稿元数据及规则")
    public Result<DocumentVO> update(@Valid @RequestBody DocumentUpdateForm form) {
        DocumentVO data=documentService.update(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 核对来源与双索引后发布。 */
    @PostMapping("/publish")
    @io.swagger.v3.oas.annotations.Operation(summary = "核对来源与双索引后发布")
    public Result<DocumentVO> publish(@Valid @RequestBody DocumentPublishForm form) {
        DocumentVO data=documentService.publish(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 同事务退役并发布新版。 */
    @PostMapping("/replace")
    @io.swagger.v3.oas.annotations.Operation(summary = "同事务退役并发布新版")
    public Result<DocumentVO> replace(@Valid @RequestBody DocumentPublishForm form) {
        DocumentVO data=documentService.publish(currentActor.requireIdentity(),form);return Result.success(data);
    }
    /** 停用立即撤回召回授权。 */
    @PostMapping("/disable")
    @io.swagger.v3.oas.annotations.Operation(summary = "停用立即撤回召回授权")
    public Result<DocumentVO> disable(@Valid @RequestBody IdVersionForm form) {
        DocumentVO data=documentService.availability(currentActor.requireIdentity(),form,false);return Result.success(data);
    }
    /** 重新核验后恢复当前修订。 */
    @PostMapping("/restore")
    @io.swagger.v3.oas.annotations.Operation(summary = "重新核验后恢复当前修订")
    public Result<DocumentVO> restore(@Valid @RequestBody IdVersionForm form) {
        DocumentVO data=documentService.availability(currentActor.requireIdentity(),form,true);return Result.success(data);
    }
    /** 仅安全收敛且从未发布的草稿可逻辑删除。 */
    @PostMapping("/delete")
    @io.swagger.v3.oas.annotations.Operation(summary = "删除已收敛草稿")
    public Result<Void> delete(@Valid @RequestBody IdVersionForm form) {
        documentService.deleteDraft(currentActor.requireIdentity(),form);return Result.success();
    }
    /** 上传只提供服务端原件流，原请求未知时保留原键。 */
    @PostMapping(value = "/upload",consumes = "multipart/form-data")
    @io.swagger.v3.oas.annotations.Operation(summary = "上传PDF或UTF-8 Markdown原件")
    public org.springframework.http.ResponseEntity<Result<DocumentVO>> upload(
            @Valid @org.springframework.web.bind.annotation.RequestPart("metadata") DocumentUploadForm metadata,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        var actor=currentActor.requireIdentity();
        try(var input=file.getInputStream()) {
            DocumentVO data=documentService.upload(actor,metadata,file.getOriginalFilename(),file.getContentType(),input);
            return org.springframework.http.ResponseEntity.status(201).body(Result.success(data));
        }
    }

}
