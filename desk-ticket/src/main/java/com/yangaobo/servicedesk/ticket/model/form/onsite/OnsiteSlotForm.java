package com.yangaobo.servicedesk.ticket.model.form.onsite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人工出勤核对后的时段输入。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OnsiteSlotForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务区域。
     */
    @Schema(description = "服务区域")
    @NotBlank
    @Pattern(regexp = "[A-Z0-9_-]{1,32}")
    private String serviceAreaCode;

    /**
     * 带时区的窗口开始。
     */
    @Schema(description = "带时区的窗口开始")
    @NotNull
    private OffsetDateTime startsAt;

    /**
     * 带时区的开放时刻。
     */
    @Schema(description = "带时区的开放时刻")
    @NotNull
    private OffsetDateTime bookingOpensAt;

    /**
     * 带时区的截止时刻。
     */
    @Schema(description = "带时区的截止时刻")
    @NotNull
    private OffsetDateTime bookingClosesAt;

    /**
     * 出勤容量，1至1000。
     */
    @Schema(description = "出勤容量，1至1000")
    @NotNull
    @Min(1)
    @Max(1000)
    private Integer capacity;
}
