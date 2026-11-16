package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.validator.Identifier;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新版发布或替换时明确预期的当前修订。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentPublishForm extends IdVersionForm {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 首次发布为空，替换须指定同系列较旧的当前修订。
     */
    @Identifier
    private String replacesDocumentId;
}
