package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 系列、修订及索引任务查询。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
public class KnowledgeQueryForm extends com.yangaobo.servicedesk.common.base.BasePageForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 按资源标识查询。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "按资源标识查询")
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String id;

    /** 所属系列筛选。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "所属系列筛选")
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String seriesId;

    /** 所属修订筛选。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "所属修订筛选")
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String documentId;

    /** 按当前发起者原请求查询。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "按当前发起者原请求查询")
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String clientRequestId;

    /** 当前上传者原上传键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前上传者原上传键")
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String clientUploadId;
}
