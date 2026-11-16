package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.validator.Identifier;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportContextForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工单分类与环境变更的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketContextForm extends TicketActionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类目标项目，未分类可空，已分类不能清空。
     */
    @Schema(description = "分类目标项目，未分类可空，已分类不能清空。")
    @Identifier
    private String serviceItemId;

    /**
     * 完整替换后的固定环境。
     */
    @Schema(description = "完整替换后的固定环境。")
    @Valid
    @NotNull
    private SupportContextForm supportContext;

    /**
     * 工单现场区域，可清空。
     */
    @Schema(description = "工单现场区域，可清空。")
    @Pattern(regexp = "[A-Z0-9_-]{1,32}")
    private String serviceAreaCode;

    /**
     * 工单现场地点，可清空。
     */
    @Schema(description = "工单现场地点，可清空。")
    @Size(max = 300)
    private String serviceLocation;

    /**
     * 本次变更原因。
     */
    @Schema(description = "本次变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
