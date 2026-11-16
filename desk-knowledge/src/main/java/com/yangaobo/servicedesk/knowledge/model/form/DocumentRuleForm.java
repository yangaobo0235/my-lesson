package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目与可选型号环境的精确适用规则。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class DocumentRuleForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 必填服务项目。
     */
    @Schema(description = "必填服务项目")
    @NotNull
    @Identifier
    private String serviceItemId;

    /**
     * 可选型号。
     */
    @Schema(description = "可选型号")
    @Identifier
    private String productModelId;

    /**
     * 系统名称。
     */
    @Schema(description = "系统名称")
    @Size(max = 100)
    private String osName;

    /**
     * 系统精确版本。
     */
    @Schema(description = "系统精确版本")
    @Size(max = 100)
    private String osVersion;

    /**
     * 软件名称。
     */
    @Schema(description = "软件名称")
    @Size(max = 100)
    private String softwareName;

    /**
     * 软件精确版本。
     */
    @Schema(description = "软件精确版本")
    @Size(max = 100)
    private String softwareVersion;
}
