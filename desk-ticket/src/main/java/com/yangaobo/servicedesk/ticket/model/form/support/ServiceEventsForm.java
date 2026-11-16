package com.yangaobo.servicedesk.ticket.model.form.support;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 服务审计分页筛选的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ServiceEventsForm extends BasePageForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目或资格标识。
     */
    @Schema(description = "项目或资格标识。")
    @NotBlank
    @Identifier
    private String id;
}
