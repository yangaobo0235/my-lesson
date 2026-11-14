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
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentEventVO;

/**
 * 知识不可变审计。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/document-event")
@lombok.RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "知识不可变审计")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentEventAdminController {
    private final CurrentActor currentActor;
    private final IDocumentService documentService;
    /** 分页查看资料动作历史。 */
    @PostMapping("/list")
    @io.swagger.v3.oas.annotations.Operation(summary = "分页查看资料动作历史")
    public Result<PageVO<DocumentEventVO>> list(@Valid @RequestBody KnowledgeQueryForm form) {
        PageVO<DocumentEventVO> data=documentService.events(currentActor.requireIdentity(),form);return Result.success(data);
    }

}
