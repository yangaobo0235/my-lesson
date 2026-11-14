package com.yangaobo.servicedesk.api.user.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * VerifyBrowserForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class VerifyBrowserForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 仅交给身份所有者核验的原Cookie，不记录日志。 */
    @NotBlank
    @Size(max = 8192)
    @lombok.ToString.Exclude
    private String cookie;

    /** 原始HTTP方法。 */
    @NotBlank
    @Pattern(regexp = "GET|HEAD|OPTIONS|POST|PUT|PATCH|DELETE")
    private String method;

    /** 原请求CSRF头，只读请求可空。 */
    @Size(max = 256)
    @lombok.ToString.Exclude
    private String csrfToken;
}
