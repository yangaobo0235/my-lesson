package com.yangaobo.servicedesk.ticket.model.form.onsite;

/**
 * 人工出勤核对后的时段输入。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class OnsiteSlotForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 服务区域。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "服务区域")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Z0-9_-]{1,32}")
    private String serviceAreaCode;

    /** 带时区的窗口开始。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "带时区的窗口开始")
    @jakarta.validation.constraints.NotNull
    private java.time.OffsetDateTime startsAt;

    /** 带时区的开放时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "带时区的开放时刻")
    @jakarta.validation.constraints.NotNull
    private java.time.OffsetDateTime bookingOpensAt;

    /** 带时区的截止时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "带时区的截止时刻")
    @jakarta.validation.constraints.NotNull
    private java.time.OffsetDateTime bookingClosesAt;

    /** 出勤容量，1至1000。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "出勤容量，1至1000")
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1000)
    private Integer capacity;
}
