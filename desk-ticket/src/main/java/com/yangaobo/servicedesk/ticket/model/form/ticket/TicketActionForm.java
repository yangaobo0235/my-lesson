package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.base.VersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工单幂等动作的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketActionForm extends VersionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 同一次动作的原请求键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "同一次动作的原请求键。")
    @NotBlank @Identifier private String clientActionId;
}
