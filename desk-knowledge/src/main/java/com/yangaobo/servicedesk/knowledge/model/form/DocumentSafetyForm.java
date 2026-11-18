package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识风险撤回和访问授权变更，原动作键在重试时保持不变。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentSafetyForm extends IdVersionForm {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 操作者及修订范围内的动作幂等键。
     */
    @NotBlank
    @Identifier
    private String clientActionId;

    /**
     * 本次撤回、撤权或恢复访问的人工核对依据。
     */
    @NotBlank
    @Size(max = 1000)
    private String reason;
}
