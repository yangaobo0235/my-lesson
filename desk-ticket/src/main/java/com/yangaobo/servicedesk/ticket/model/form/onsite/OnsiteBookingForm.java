package com.yangaobo.servicedesk.ticket.model.form.onsite;

/**
 * 客户原键预约输入。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class OnsiteBookingForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 时段标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "时段标识")
    @jakarta.validation.constraints.NotNull @com.yangaobo.servicedesk.common.validator.Identifier
    private String slotId;

    /** 原请求键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原请求键")
    @jakarta.validation.constraints.NotNull @com.yangaobo.servicedesk.common.validator.Identifier
    private String clientRequestId;
}
