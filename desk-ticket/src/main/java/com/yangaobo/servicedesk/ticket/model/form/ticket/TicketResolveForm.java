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
 * 人工处理结论的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketResolveForm extends TicketActionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 公开处理说明。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "公开处理说明。")
    @NotBlank
    @Size(max = 5000)
    private String content;

    /** 恢复、指导或转介。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "恢复、指导或转介。")
    @NotBlank
    @Pattern(regexp = "RESTORED|GUIDANCE|REFERRED")
    private String resolutionType;

    /** 转介去向，转介时必填。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "转介去向，转介时必填。")
    @Size(max = 300)
    private String referralTarget;

    /** 可选处理草稿。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选处理草稿。")
    @Identifier private String sourceDraftId;
}
