package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 系列、修订及索引任务查询。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KnowledgeQueryForm extends BasePageForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 按资源标识查询。
     */
    @Schema(description = "按资源标识查询")
    @Identifier
    private String id;

    /**
     * 所属系列筛选。
     */
    @Schema(description = "所属系列筛选")
    @Identifier
    private String seriesId;

    /**
     * 所属修订筛选。
     */
    @Schema(description = "所属修订筛选")
    @Identifier
    private String documentId;

    /**
     * 按当前发起者原请求查询。
     */
    @Schema(description = "按当前发起者原请求查询")
    @Identifier
    private String clientRequestId;

    /**
     * 当前上传者原上传键。
     */
    @Schema(description = "当前上传者原上传键")
    @Identifier
    private String clientUploadId;
}
