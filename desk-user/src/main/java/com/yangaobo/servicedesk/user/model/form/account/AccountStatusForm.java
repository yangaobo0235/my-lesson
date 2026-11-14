package com.yangaobo.servicedesk.user.model.form.account;

import com.yangaobo.servicedesk.common.base.IdVersionForm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账号启停的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AccountStatusForm extends IdVersionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 停用或启用原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "停用或启用原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
