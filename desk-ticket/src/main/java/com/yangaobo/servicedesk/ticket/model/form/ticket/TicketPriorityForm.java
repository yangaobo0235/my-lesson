package com.yangaobo.servicedesk.ticket.model.form.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 受理优先级调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketPriorityForm extends TicketActionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 工单优先级。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "工单优先级。")
    @NotBlank
    @Pattern(regexp = "NORMAL|HIGH")
    private String priority;

    /** 本次变更原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本次变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
