package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.annotation.SchemaEnum;
import com.yangaobo.servicedesk.ticket.model.enums.TicketPriorityEnum;
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
 * 受理优先级调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketPriorityForm extends TicketActionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工单优先级。
     */
    @Schema(description = "工单优先级。")
    @NotBlank
    @SchemaEnum(TicketPriorityEnum.class)
    @Pattern(regexp = "NORMAL|HIGH")
    private String priority;

    /**
     * 本次变更原因。
     */
    @Schema(description = "本次变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
