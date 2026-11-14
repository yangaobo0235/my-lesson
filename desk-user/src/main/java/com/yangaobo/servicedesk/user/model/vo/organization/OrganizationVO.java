package com.yangaobo.servicedesk.user.model.vo.organization;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 组织的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OrganizationVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 组织标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "组织标识。")
    private String id;

    /** 稳定代码。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "稳定代码。")
    private String organizationCode;

    /** 组织名称。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "组织名称。")
    private String organizationName;

    /** 组织类型。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "组织类型。")
    private String organizationType;

    /** 组织状态。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "组织状态。")
    private String status;

    /** 当前版本。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前版本。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
