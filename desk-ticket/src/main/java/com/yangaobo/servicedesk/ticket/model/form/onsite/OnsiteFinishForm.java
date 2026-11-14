package com.yangaobo.servicedesk.ticket.model.form.onsite;

/**
 * 当前处理人登记的真实履约。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class OnsiteFinishForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** VISITED或NOT_PERFORMED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "VISITED或NOT_PERFORMED")
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp = "VISITED|NOT_PERFORMED")
    private String finishResult;
}
