package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.base.VersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工单幂等动作的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketActionForm extends VersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 同一次动作的原请求键。
     */
    @Schema(description = "同一次动作的原请求键。")
    @NotBlank
    @Identifier
    private String clientActionId;
}
