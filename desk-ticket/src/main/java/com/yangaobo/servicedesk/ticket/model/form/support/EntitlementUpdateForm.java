package com.yangaobo.servicedesk.ticket.model.form.support;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 服务资格日期调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EntitlementUpdateForm extends IdVersionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 同一次动作的原请求键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "同一次动作的原请求键。")
    @NotBlank @Identifier private String clientActionId;

    /** 首个有效日。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "首个有效日。")
    @NotNull private java.time.LocalDate startsOn;

    /** 最后有效日。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "最后有效日。")
    @NotNull private java.time.LocalDate endsOn;

    /** 本次变更原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本次变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
