package com.yangaobo.servicedesk.user.model.form.auth;

import com.yangaobo.servicedesk.common.base.IdVersionForm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员重置口令的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PasswordResetForm extends IdVersionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 重置口令，下次登录须改密。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "重置口令，下次登录须改密。")
    @NotBlank
    @Size(max = 128)
    @lombok.ToString.Exclude
    private String newPassword;

    /** 重置原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "重置原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
