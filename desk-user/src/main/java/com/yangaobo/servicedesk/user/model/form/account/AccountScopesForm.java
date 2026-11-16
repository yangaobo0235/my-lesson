package com.yangaobo.servicedesk.user.model.form.account;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
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
 * 人员客户范围变更的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AccountScopesForm extends IdVersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 完整客户范围，空集合表示全部撤销。
     */
    @Schema(description = "完整客户范围，空集合表示全部撤销。")
    @NotNull
    @Size(max = 100)
    private Set<@NotBlank @Identifier String> customerIds;

    /**
     * 范围变更原因。
     */
    @Schema(description = "范围变更原因。")
    @NotBlank
    @Size(max = 500)
    private String reason;
}
