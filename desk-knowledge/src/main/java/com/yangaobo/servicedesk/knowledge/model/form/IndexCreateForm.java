package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 当前草稿版本下的新索引尝试。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IndexCreateForm extends IdVersionForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原索引请求键。
     */
    @Schema(description = "原索引请求键")
    @NotNull
    @Identifier
    private String clientRequestId;

    /**
     * 固定解析、空间与预算。
     */
    @Schema(description = "固定解析、空间与预算")
    @Valid
    @NotNull
    private IndexProcessingConfig processingConfig;
}
