package com.yangaobo.servicedesk.user.model.form.organization;

import com.yangaobo.servicedesk.common.base.IdVersionForm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 组织资料调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OrganizationUpdateForm extends IdVersionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 组织名称。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "组织名称。")
    @NotBlank
    @Size(max = 100)
    private String organizationName;

    /** 组织状态。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "组织状态。")
    @NotBlank
    @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;

    /** 变更原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
