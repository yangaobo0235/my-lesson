package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 审核规程校验动作，同一请求重试保留原键及预期版本。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentProcedureForm extends IdVersionForm {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 操作者与修订范围的唯一动作键。
     */
    @NotBlank
    @Identifier
    @Schema(description = "原规程校验动作键")
    private String clientActionId;
}
