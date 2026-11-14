package com.yangaobo.servicedesk.user.model.form.auth;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 身份资源分页筛选的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class IdentityQueryForm extends BasePageForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 可选组织过滤。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选组织过滤。")
    @Identifier private String organizationId;

    /** 可选状态过滤。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选状态过滤。")
    @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;

    /** 账号或名称前缀。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "账号或名称前缀。")
    @Size(max = 64)
    private String keyword;
}
