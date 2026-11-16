package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 仅草稿允许修改的元数据与规则。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DocumentUpdateForm extends IdVersionForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 资料标题。
     */
    @Schema(description = "资料标题")
    @NotBlank
    @Size(max = 255)
    private String title;

    /**
     * 人工登记的来源说明链接，不自动抓取。
     */
    @Schema(description = "人工登记的来源说明链接，不自动抓取")
    @Size(max = 2000)
    private String sourceUrl;

    /**
     * 来源与使用依据复核记录，发布必填。
     */
    @Schema(description = "来源与使用依据复核记录，发布必填")
    @Size(max = 1000)
    private String sourceReviewNote;

    /**
     * 原件修订标签。
     */
    @Schema(description = "原件修订标签")
    @Size(max = 100)
    private String revisionLabel;

    /**
     * 原件发布日期，可空。
     */
    @Schema(description = "原件发布日期，可空")
    private LocalDate issuedOn;

    /**
     * 人可读的适用说明。
     */
    @Schema(description = "人可读的适用说明")
    @Size(max = 500)
    private String applicabilityNote;

    /**
     * 一条内AND，多条间OR。
     */
    @Schema(description = "一条内AND，多条间OR")
    @Valid
    @NotEmpty
    @Size(max = 50)
    private List<DocumentRuleForm> rules;
}
