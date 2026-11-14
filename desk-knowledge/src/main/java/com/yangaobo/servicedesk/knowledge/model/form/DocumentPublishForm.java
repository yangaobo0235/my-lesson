package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 新版发布或替换时明确预期的当前修订。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.EqualsAndHashCode(callSuper = true)
public class DocumentPublishForm extends com.yangaobo.servicedesk.common.base.IdVersionForm {
    @java.io.Serial private static final long serialVersionUID = 1L;
    /** 首次发布为空，替换须指定同系列较旧的当前修订。 */
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String replacesDocumentId;
}
