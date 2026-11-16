package com.yangaobo.servicedesk.ticket.model.form.onsite;

import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户原键预约输入。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OnsiteBookingForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 时段标识。
     */
    @Schema(description = "时段标识")
    @NotNull
    @Identifier
    private String slotId;

    /**
     * 原请求键。
     */
    @Schema(description = "原请求键")
    @NotNull
    @Identifier
    private String clientRequestId;
}
