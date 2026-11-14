package com.yangaobo.servicedesk.knowledge.model.vo;

/**
 * 不可变原件修订与发布事实的公开投影。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class DocumentVO implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键，由所属后端生成。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键，由所属后端生成")
    private String id;

    /** 本地 FK → kb_document_series。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地 FK → kb_document_series")
    private String seriesId;

    /** 上传者范围的客户端上传请求键，重试复用。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "上传者范围的客户端上传请求键，重试复用")
    private String clientUploadId;

    /** 系列内递增序号，锁系列后分配。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "系列内递增序号，锁系列后分配")
    private Integer revisionNumber;

    /** 真实文档标题。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "真实文档标题")
    private String title;

    /** PDF / MARKDOWN，创建后固定。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "PDF / MARKDOWN，创建后固定")
    private String contentFormat;

    /** 原文件名，仅展示，Markdown为UTF-8原件。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原文件名，仅展示，Markdown为UTF-8原件")
    private String originalFileName;

    /** 正数，PDF至多20MiB、MARKDOWN至多1MiB。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "正数，PDF至多20MiB、MARKDOWN至多1MiB")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long fileSizeBytes;

    /** 流式摘要，同摘要的资料可有不同授权。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "流式摘要，同摘要的资料可有不同授权")
    private String fileSha256;

    /** 原始来源说明，不作为自动抓取地址。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原始来源说明，不作为自动抓取地址")
    private String sourceUrl;

    /** 复核出处与使用依据，脱敏案例记录审批依据，发布必填。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "复核出处与使用依据，脱敏案例记录审批依据，发布必填")
    private String sourceReviewNote;

    /** 原文已知修订标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原文已知修订标识")
    private String revisionLabel;

    /** 原文发布日期。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原文发布日期")
    private java.time.LocalDate issuedOn;

    /** 人类可读适用条件摘要，实际匹配使用kb_document_applicability。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "人类可读适用条件摘要，实际匹配使用kb_document_applicability")
    private String applicabilityNote;

    /** DRAFT / PUBLISHED / DISABLED / RETIRED / DELETED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "DRAFT / PUBLISHED / DISABLED / RETIRED / DELETED")
    private String publicationStatus;

    /** 本地复合 FK(current_job_id,id) → kb_index_job(id,document_id)，外部 AI 任务使用同一 ID。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地复合 FK(current_job_id,id) → kb_index_job(id,document_id)，外部 AI 任务使用同一 ID")
    private String currentJobId;

    /** 本地复合 FK(replaces_document_id,series_id) → kb_document(id,series_id)，发布替换时写入。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地复合 FK(replaces_document_id,series_id) → kb_document(id,series_id)，发布替换时写入")
    private String replacesDocumentId;

    /** 首次成功发布时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "首次成功发布时刻")
    private java.time.Instant publishedAt;

    /** 外部知识管理员账号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "外部知识管理员账号")
    private String publishedBy;

    /** publication_status=DELETED 时必填。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "publication_status=DELETED 时必填")
    private java.time.Instant deletedAt;

    /** 外部上传人账号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "外部上传人账号")
    private String createdBy;

    /** 记录创建时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "记录创建时间")
    private java.time.Instant createdAt;

    /** 更新时显式维护，默认值不自动随 UPDATE 改变。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "更新时显式维护，默认值不自动随 UPDATE 改变")
    private java.time.Instant updatedAt;

    /** 非负，更新比较旧值后加一。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "非负，更新比较旧值后加一")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long rowVersion;
    /** 当前草稿或固定发布规则，不把适用规则当成客户权限。 */
    private java.util.List<com.yangaobo.servicedesk.knowledge.model.form.DocumentRuleForm> rules;
}
