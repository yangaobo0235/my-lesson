package com.yangaobo.servicedesk.ticket.model.form.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 现场资格版本化动作。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OnsiteEligibilityForm extends TicketActionForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人工确认的现场资格。
     */
    @Schema(description = "人工确认的现场资格")
    @NotNull
    private Boolean onsiteEligible;

    /**
     * 核对或撤回原因。
     */
    @Schema(description = "核对或撤回原因")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
