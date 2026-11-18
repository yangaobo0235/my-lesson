package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.annotation.SchemaEnum;
import com.yangaobo.servicedesk.common.validator.Identifier;
import com.yangaobo.servicedesk.ticket.model.enums.VisibilityEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 公开或内部处理记录的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketCommentForm extends TicketActionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理记录正文。
     */
    @Schema(description = "处理记录正文。")
    @NotBlank
    @Size(max = 5000)
    private String content;

    /**
     * 公开或内部。
     */
    @Schema(description = "公开或内部。")
    @NotBlank
    @SchemaEnum(VisibilityEnum.class)
    @Pattern(regexp = "PUBLIC|INTERNAL")
    private String visibility;

    /**
     * 可选动作草稿。
     */
    @Schema(description = "可选动作草稿。")
    @Identifier
    private String sourceDraftId;
    /**
     * 草稿根标识，只用于本地回执对账，新动作仍由内部RPC验证。
     */
    @Identifier
    private String sourceDraftRootId;
    /**
     * 用户确认的不可变内容摘要。
     */
    @Pattern(regexp = "[0-9a-f]{64}")
    private String contentSha256;
}
