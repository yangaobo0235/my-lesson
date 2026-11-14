package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工单分派与转派的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketAssignmentForm extends TicketActionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 当前有资格的技术人员。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前有资格的技术人员。")
    @NotBlank @Identifier private String assigneeId;

    /** 本次变更原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本次变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
