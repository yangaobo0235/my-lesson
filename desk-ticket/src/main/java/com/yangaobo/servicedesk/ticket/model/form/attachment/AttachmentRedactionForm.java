package com.yangaobo.servicedesk.ticket.model.form.attachment;

import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 原上传者确认脱敏文本及 AI 使用授权，版本指附件行版本。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "确认附件脱敏版本")
public class AttachmentRedactionForm extends TicketActionForm {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 人工预览后的完整脱敏文本，另校验 UTF-8 字节预算。
     */
    @NotBlank
    @Size(max = 1048576)
    @Schema(description = "已预览的脱敏文本，至多 1MiB")
    private String redactedText;
    /**
     * 用户核对的原件摘要。
     */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}")
    @Schema(description = "原件 SHA-256")
    private String fileSha256;
    /**
     * 明确允许或撤销 AI 使用，不能用缺省值代替确认。
     */
    @NotNull
    @Schema(description = "是否允许 AI 使用当前脱敏版本")
    private Boolean allowAiUse;
}
