package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 当前草稿版本下的新索引尝试。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
public class IndexCreateForm extends com.yangaobo.servicedesk.common.base.IdVersionForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 原索引请求键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原索引请求键")
    @jakarta.validation.constraints.NotNull @com.yangaobo.servicedesk.common.validator.Identifier
    private String clientRequestId;

    /** 固定解析、空间与预算。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "固定解析、空间与预算")
    @jakarta.validation.Valid @jakarta.validation.constraints.NotNull
    private IndexProcessingConfig processingConfig;
}
