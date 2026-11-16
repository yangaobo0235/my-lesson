package com.yangaobo.servicedesk.ticket.model.form.support;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 服务资格日期调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EntitlementUpdateForm extends IdVersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 同一次动作的原请求键。
     */
    @Schema(description = "同一次动作的原请求键。")
    @NotBlank
    @Identifier
    private String clientActionId;

    /**
     * 首个有效日。
     */
    @Schema(description = "首个有效日。")
    @NotNull
    private LocalDate startsOn;

    /**
     * 最后有效日。
     */
    @Schema(description = "最后有效日。")
    @NotNull
    private LocalDate endsOn;

    /**
     * 本次变更原因。
     */
    @Schema(description = "本次变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
