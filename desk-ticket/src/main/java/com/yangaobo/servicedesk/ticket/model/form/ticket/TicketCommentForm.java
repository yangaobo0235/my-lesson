package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开或内部处理记录的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketCommentForm extends TicketActionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 处理记录正文。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "处理记录正文。")
    @NotBlank
    @Size(max = 5000)
    private String content;

    /** 公开或内部。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "公开或内部。")
    @NotBlank
    @Pattern(regexp = "PUBLIC|INTERNAL")
    private String visibility;

    /** 可选动作草稿。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选动作草稿。")
    @Identifier private String sourceDraftId;
}
