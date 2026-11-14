package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 具有稳定来源与受众的系列输入。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class SeriesCreateForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** OFFICIAL_MANUAL、TROUBLESHOOTING_GUIDE或SANITIZED_CASE。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "OFFICIAL_MANUAL、TROUBLESHOOTING_GUIDE或SANITIZED_CASE")
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp = "OFFICIAL_MANUAL|TROUBLESHOOTING_GUIDE|SANITIZED_CASE")
    private String sourceType;

    /** 来源命名空间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "来源命名空间")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64)
    private String sourceNamespace;

    /** 稳定来源代码。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "稳定来源代码")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120)
    private String sourceCode;

    /** 原件发布组织。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原件发布组织")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200)
    private String sourceOrganization;

    /** 语言代码。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "语言代码")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 16)
    private String languageCode;

    /** CUSTOMER或SERVICE_STAFF。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "CUSTOMER或SERVICE_STAFF")
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp = "CUSTOMER|SERVICE_STAFF")
    private String audience;
}
