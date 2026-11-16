package com.yangaobo.servicedesk.ticket.model.form.support;

import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IT服务项目的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ServiceItemCreateForm implements Serializable {

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
     * 稳定项目代码。
     */
    @Schema(description = "稳定项目代码。")
    @NotBlank
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}")
    private String serviceCode;

    /**
     * 四类办公IT支持类别。
     */
    @Schema(description = "四类办公IT支持类别。")
    @NotBlank
    @Pattern(regexp = "TERMINAL|PRINTING|NETWORK|OFFICE_SOFTWARE")
    private String category;

    /**
     * 项目名称。
     */
    @Schema(description = "项目名称。")
    @NotBlank
    @Size(max = 100)
    private String serviceName;

    /**
     * 明确支持环境及排除条件。
     */
    @Schema(description = "明确支持环境及排除条件。")
    @NotBlank
    @Size(max = 5000)
    private String supportScope;

    /**
     * 是否支持人工开放现场服务。
     */
    @Schema(description = "是否支持人工开放现场服务。")
    @NotNull
    private Boolean onsiteSupported;

    /**
     * 本次变更原因。
     */
    @Schema(description = "本次变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
