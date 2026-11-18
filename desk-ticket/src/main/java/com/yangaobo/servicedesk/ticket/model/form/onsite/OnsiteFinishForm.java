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
@lombok.EqualsAndHashCode(callSuper = true)
public class OnsiteFinishForm extends com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm implements Serializable {
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
    /**
     * 已核验的实际执行人，未执行同样明确本次负责人员。
     */
    @NotNull
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String performedBy;
    /**
     * 现场结果说明，未执行必须明确原因。
     */
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 1000)
    private String finishNote;
}
