package com.yangaobo.servicedesk.api.user.form;

import com.yangaobo.servicedesk.common.validator.Identifier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VerifySessionForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class VerifySessionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 框架会话主键引用。
     */
    @NotBlank
    @Size(max = 36)
    @lombok.ToString.Exclude
    private String sessionRef;

    /**
     * 原会话拥有者。
     */
    @NotBlank
    @Identifier
    private String ownerId;

    /**
     * 原身份版本。
     */
    @NotNull
    @Min(0)
    private Long authVersion;
}
