package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 仅草稿允许修改的元数据与规则。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
public class DocumentUpdateForm extends com.yangaobo.servicedesk.common.base.IdVersionForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 资料标题。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "资料标题")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 255)
    private String title;

    /** 人工登记的来源说明链接，不自动抓取。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "人工登记的来源说明链接，不自动抓取")
    @jakarta.validation.constraints.Size(max = 2000)
    private String sourceUrl;

    /** 来源与使用依据复核记录，发布必填。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "来源与使用依据复核记录，发布必填")
    @jakarta.validation.constraints.Size(max = 1000)
    private String sourceReviewNote;

    /** 原件修订标签。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原件修订标签")
    @jakarta.validation.constraints.Size(max = 100)
    private String revisionLabel;

    /** 原件发布日期，可空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原件发布日期，可空")
    private java.time.LocalDate issuedOn;

    /** 人可读的适用说明。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "人可读的适用说明")
    @jakarta.validation.constraints.Size(max = 500)
    private String applicabilityNote;

    /** 一条内AND，多条间OR。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "一条内AND，多条间OR")
    @jakarta.validation.Valid @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 50)
    private java.util.List<DocumentRuleForm> rules;
}
