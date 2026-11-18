package com.yangaobo.servicedesk.ticket.controller.app;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.DiagnosticText;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentRedactionForm;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentUploadForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.vo.attachment.AttachmentVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.security.CurrentActor;
import com.yangaobo.servicedesk.ticket.service.IAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件 HTTP 边界，文件传输有界且下载不允许浏览器解释原件内容。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "工单诊断附件")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentController {
    private final IAttachmentService attachmentService;
    private final CurrentActor currentActor;

    /**
     * 先获取当前身份再读取有界请求体；状态字段表达真实存储阶段。
     */
    @PostMapping(value = "/tickets/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传诊断文本")
    public Result<AttachmentVO> upload(@PathVariable String id, @Valid @RequestPart("metadata") AttachmentUploadForm form,
                                       @RequestPart("file") MultipartFile file) throws IOException {
        var actor = currentActor.requireIdentity();
        if (file.isEmpty() || file.getSize() > DiagnosticText.MAX_BYTES)
            throw new BusinessException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(DiagnosticText.MAX_BYTES + 1);
        }
        AttachmentVO data = attachmentService.upload(actor, id, form, file.getOriginalFilename(), bytes);
        return Result.success(data);
    }

    /**
     * 只查询本人原上传请求，不生成新的存储外写。
     */
    @GetMapping("/attachments/by-request/{key}")
    @Operation(summary = "核对原上传意图")
    public Result<AttachmentVO> byRequest(@PathVariable String key) {
        AttachmentVO data = attachmentService.byRequest(currentActor.requireIdentity(), key);
        return Result.success(data);
    }

    /**
     * 分页只包含当前可见附件。
     */
    @GetMapping("/tickets/{id}/attachments")
    @Operation(summary = "查询工单附件")
    public Result<PageVO<AttachmentVO>> list(@PathVariable String id, @Valid BasePageForm form) {
        PageVO<AttachmentVO> data = attachmentService.list(currentActor.requireIdentity(), id, form);
        return Result.success(data);
    }

    /**
     * 附件至多 1MiB，完整核验成功后一次交付，禁止私有原件缓存。
     */
    @GetMapping("/tickets/{id}/attachments/{attachmentId}/file")
    @Operation(summary = "下载有权原件")
    public ResponseEntity<byte[]> file(@PathVariable String id, @PathVariable String attachmentId) {
        byte[] data = attachmentService.original(currentActor.requireIdentity(), id, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(data.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=diagnostic.txt")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(data);
    }

    /**
     * 用户确认同时绑定原件摘要、附件版本和脱敏正文。
     */
    @PostMapping("/tickets/{id}/attachments/{attachmentId}/confirm-redaction")
    @Operation(summary = "确认脱敏版本与 AI 使用")
    public Result<TicketActionReceiptVO> confirm(@PathVariable String id, @PathVariable String attachmentId,
                                                 @Valid @RequestBody AttachmentRedactionForm form) {
        TicketActionReceiptVO data = attachmentService.confirmRedaction(currentActor.requireIdentity(), id, attachmentId, form);
        return Result.success(data);
    }

    /**
     * 逻辑撤销访问并保留审计，不删除唯一原件。
     */
    @PostMapping("/tickets/{id}/attachments/{attachmentId}/delete")
    @Operation(summary = "撤销附件访问")
    public Result<TicketActionReceiptVO> delete(@PathVariable String id, @PathVariable String attachmentId,
                                                @Valid @RequestBody TicketActionForm form) {
        TicketActionReceiptVO data = attachmentService.deleteAttachment(currentActor.requireIdentity(), id, attachmentId, form);
        return Result.success(data);
    }
}
