package com.yangaobo.servicedesk.ticket.model.form.attachment;

import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 上传元信息，存储定位和文件摘要由服务端产生。
 *
 * @author yangaobo
 */
@Data
@Schema(description = "附件上传意图")
public class AttachmentUploadForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 同一次上传始终复用的请求键。
     */
    @NotBlank
    @Identifier
    @Schema(description = "上传请求键")
    private String clientUploadId;
    /**
     * 服务端验证的文本格式。
     */
    @NotBlank
    @Pattern(regexp = "TEXT|LOG|JSON")
    @Schema(description = "TEXT、LOG 或 JSON")
    private String contentFormat;
    /**
     * 客户只能选择 PUBLIC。
     */
    @NotBlank
    @Pattern(regexp = "PUBLIC|INTERNAL")
    @Schema(description = "PUBLIC 或 INTERNAL")
    private String visibility;
}
