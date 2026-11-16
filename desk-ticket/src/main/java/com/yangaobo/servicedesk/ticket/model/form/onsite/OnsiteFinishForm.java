package com.yangaobo.servicedesk.ticket.model.form.onsite;

import com.yangaobo.servicedesk.common.annotation.SchemaEnum;
import com.yangaobo.servicedesk.ticket.model.enums.OnsiteFinishResultEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前处理人登记的真实履约。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OnsiteFinishForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * VISITED或NOT_PERFORMED。
     */
    @Schema(description = "VISITED或NOT_PERFORMED")
    @NotNull
    @SchemaEnum(OnsiteFinishResultEnum.class)
    @Pattern(regexp = "VISITED|NOT_PERFORMED")
    private String finishResult;
}
