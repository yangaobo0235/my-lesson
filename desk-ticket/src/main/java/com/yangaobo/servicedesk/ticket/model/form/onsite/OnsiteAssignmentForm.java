package com.yangaobo.servicedesk.ticket.model.form.onsite;

import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import com.yangaobo.servicedesk.common.validator.Identifier;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 预约动作携带原动作键和预约独立版本。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OnsiteAssignmentForm extends TicketActionForm {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 新现场负责人。
     */
    @NotNull
    @Identifier
    private String technicianId;
    /**
     * 更换原因。
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
