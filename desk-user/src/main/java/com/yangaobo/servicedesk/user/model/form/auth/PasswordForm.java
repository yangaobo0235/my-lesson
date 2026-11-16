package com.yangaobo.servicedesk.user.model.form.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 用户改密的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class PasswordForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前口令。
     */
    @Schema(description = "当前口令。")
    @NotBlank
    @Size(max = 128)
    @ToString.Exclude
    private String currentPassword;

    /**
     * 新口令，不记录日志。
     */
    @Schema(description = "新口令，不记录日志。")
    @NotBlank
    @Size(max = 128)
    @ToString.Exclude
    private String newPassword;
}
