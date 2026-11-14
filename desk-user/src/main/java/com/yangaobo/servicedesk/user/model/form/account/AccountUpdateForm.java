package com.yangaobo.servicedesk.user.model.form.account;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账号资料调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AccountUpdateForm extends IdVersionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 目标组织，变更后旧会话失效。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "目标组织，变更后旧会话失效。")
    @NotBlank @Identifier private String organizationId;

    /** 姓名。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "姓名。")
    @NotBlank
    @Size(max = 100)
    private String displayName;

    /** 变更原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
