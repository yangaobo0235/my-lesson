package com.yangaobo.servicedesk.knowledge.controller.app;

import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeReadForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentVO;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeReadService;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeViews;
import com.yangaobo.servicedesk.knowledge.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户和服务人员按支持上下文读取当前授权知识。
 *
 * @author yangaobo
 */
@RestController
@lombok.RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "适用知识读取")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeReadController {
    private final KnowledgeReadService knowledgeReadService;
    private final CurrentActor currentActor;
    /** 列表不返回OSS定位或模型推测的来源。 */
    @GetMapping("/api/v1/knowledge/documents")
    @io.swagger.v3.oas.annotations.Operation(summary = "分页查询适用发布知识")
    public Result<PageVO<DocumentVO>> list(@Valid KnowledgeReadForm form){PageVO<DocumentVO> data=knowledgeReadService.list(currentActor.requireIdentity(),form);return Result.success(data);}
    /** 当前发布及规则变化即影响读取。 */
    @GetMapping("/api/v1/knowledge/documents/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary = "查询当前有权修订")
    public Result<DocumentVO> query(@PathVariable String id,@Valid KnowledgeReadForm form){DocumentVO data=KnowledgeViews.of(knowledgeReadService.readable(currentActor.requireIdentity(),id,form));return Result.success(data);}
}
