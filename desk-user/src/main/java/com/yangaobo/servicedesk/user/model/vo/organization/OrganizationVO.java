package com.yangaobo.servicedesk.user.model.vo.organization;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OrganizationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 组织标识。
     */
    @Schema(description = "组织标识。")
    private String id;

    /**
     * 稳定代码。
     */
    @Schema(description = "稳定代码。")
    private String organizationCode;

    /**
     * 组织名称。
     */
    @Schema(description = "组织名称。")
    private String organizationName;

    /**
     * 组织类型。
     */
    @Schema(description = "组织类型。")
    private String organizationType;

    /**
     * 组织状态。
     */
    @Schema(description = "组织状态。")
    private String status;

    /**
     * 当前版本。
     */
    @Schema(description = "当前版本。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
