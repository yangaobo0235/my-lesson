package com.yangaobo.servicedesk.user.model.form.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户组织开通的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OrganizationCreateForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 客户代码，规范化小写。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "客户代码，规范化小写。")
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{2,63}")
    private String organizationCode;

    /** 客户组织名称。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "客户组织名称。")
    @NotBlank
    @Size(max = 100)
    private String organizationName;
}
