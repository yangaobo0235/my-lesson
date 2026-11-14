package com.yangaobo.servicedesk.ticket.model.form.ticket;

/**
 * 现场资格版本化动作。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
public class OnsiteEligibilityForm extends TicketActionForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 人工确认的现场资格。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "人工确认的现场资格")
    @jakarta.validation.constraints.NotNull
    private Boolean onsiteEligible;

    /** 核对或撤回原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "核对或撤回原因")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
    private String reason;
}
