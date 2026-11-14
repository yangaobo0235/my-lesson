package com.yangaobo.servicedesk.user.model.form.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录凭据的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class LoginForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 登录账号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "登录账号。")
    @NotBlank
    @Size(max = 64)
    private String username;

    /** 当前口令，不记录日志。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前口令，不记录日志。")
    @NotBlank
    @Size(max = 128)
    @lombok.ToString.Exclude
    private String password;
}
