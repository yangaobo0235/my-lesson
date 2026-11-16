package com.yangaobo.servicedesk.ticket.model.form.support;

import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户服务开通的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class EntitlementGrantForm implements Serializable {

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
     * 客户组织标识。
     */
    @Schema(description = "客户组织标识。")
    @NotBlank
    @Identifier
    private String customerId;

    /**
     * 已启用项目标识。
     */
    @Schema(description = "已启用项目标识。")
    @NotBlank
    @Identifier
    private String serviceItemId;

    /**
     * 上海时区首个有效日，含当天。
     */
    @Schema(description = "上海时区首个有效日，含当天。")
    @NotNull
    private LocalDate startsOn;

    /**
     * 上海时区最后有效日，含当天。
     */
    @Schema(description = "上海时区最后有效日，含当天。")
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
