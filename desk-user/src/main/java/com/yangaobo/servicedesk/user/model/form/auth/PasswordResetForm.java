package com.yangaobo.servicedesk.user.model.form.auth;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 管理员重置口令的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PasswordResetForm extends IdVersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 重置口令，下次登录须改密。
     */
    @Schema(description = "重置口令，下次登录须改密。")
    @NotBlank
    @Size(max = 128)
    @ToString.Exclude
    private String newPassword;

    /**
     * 重置原因。
     */
    @Schema(description = "重置原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
