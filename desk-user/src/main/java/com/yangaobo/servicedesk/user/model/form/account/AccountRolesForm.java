package com.yangaobo.servicedesk.user.model.form.account;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 账号角色变更的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AccountRolesForm extends IdVersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 完整目标角色集合。
     */
    @Schema(description = "完整目标角色集合。")
    @NotNull
    @Size(min = 1, max = 4)
    private Set<@NotBlank String> roles;

    /**
     * 授权变更原因。
     */
    @Schema(description = "授权变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
