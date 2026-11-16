package com.yangaobo.servicedesk.knowledge.model.form;

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
 * 具有稳定来源与受众的系列输入。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class SeriesCreateForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * OFFICIAL_MANUAL、TROUBLESHOOTING_GUIDE或SANITIZED_CASE。
     */
    @Schema(description = "OFFICIAL_MANUAL、TROUBLESHOOTING_GUIDE或SANITIZED_CASE")
    @NotNull
    @Pattern(regexp = "OFFICIAL_MANUAL|TROUBLESHOOTING_GUIDE|SANITIZED_CASE")
    private String sourceType;

    /**
     * 来源命名空间。
     */
    @Schema(description = "来源命名空间")
    @NotBlank
    @Size(max = 64)
    private String sourceNamespace;

    /**
     * 稳定来源代码。
     */
    @Schema(description = "稳定来源代码")
    @NotBlank
    @Size(max = 120)
    private String sourceCode;

    /**
     * 原件发布组织。
     */
    @Schema(description = "原件发布组织")
    @NotBlank
    @Size(max = 200)
    private String sourceOrganization;

    /**
     * 语言代码。
     */
    @Schema(description = "语言代码")
    @NotBlank
    @Size(max = 16)
    private String languageCode;

    /**
     * CUSTOMER或SERVICE_STAFF。
     */
    @Schema(description = "CUSTOMER或SERVICE_STAFF")
    @NotNull
    @Pattern(regexp = "CUSTOMER|SERVICE_STAFF")
    private String audience;
}
