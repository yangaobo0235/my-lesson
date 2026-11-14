package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 项目与可选型号环境的精确适用规则。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class DocumentRuleForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 必填服务项目。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "必填服务项目")
    @jakarta.validation.constraints.NotNull @com.yangaobo.servicedesk.common.validator.Identifier
    private String serviceItemId;

    /** 可选型号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选型号")
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String productModelId;

    /** 系统名称。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "系统名称")
    @jakarta.validation.constraints.Size(max = 100)
    private String osName;

    /** 系统精确版本。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "系统精确版本")
    @jakarta.validation.constraints.Size(max = 100)
    private String osVersion;

    /** 软件名称。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "软件名称")
    @jakarta.validation.constraints.Size(max = 100)
    private String softwareName;

    /** 软件精确版本。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "软件精确版本")
    @jakarta.validation.constraints.Size(max = 100)
    private String softwareVersion;
}
