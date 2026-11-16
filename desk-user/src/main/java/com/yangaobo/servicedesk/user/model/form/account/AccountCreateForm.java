package com.yangaobo.servicedesk.user.model.form.account;

import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 账号开通的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class AccountCreateForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 归属组织。
     */
    @Schema(description = "归属组织。")
    @NotBlank
    @Identifier
    private String organizationId;

    /**
     * 账号，规范化小写。
     */
    @Schema(description = "账号，规范化小写。")
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}")
    private String username;

    /**
     * 姓名。
     */
    @Schema(description = "姓名。")
    @NotBlank
    @Size(max = 100)
    private String displayName;

    /**
     * 初始口令，首次登录必须修改。
     */
    @Schema(description = "初始口令，首次登录必须修改。")
    @NotBlank
    @Size(max = 128)
    @ToString.Exclude
    private String initialPassword;

    /**
     * 受组织类型限制的固定角色。
     */
    @Schema(description = "受组织类型限制的固定角色。")
    @NotNull
    @Size(min = 1, max = 4)
    private Set<@NotBlank String> roles;
}
