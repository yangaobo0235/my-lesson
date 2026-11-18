package com.yangaobo.servicedesk.knowledge.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.JsonNode;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentRuleForm;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 不可变原件修订与发布事实的公开投影。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class DocumentVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 不可变人工规程，无规程资料为空。
     */
    @Schema(description = "修订内不可变人工规程")
    private JsonNode procedureDefinition;
    /**
     * 当前定义的规范摘要。
     */
    @Schema(description = "当前规程摘要")
    private String procedureSha256;
    /**
     * 已通过校验的定义摘要。
     */
    @Schema(description = "校验通过的规程摘要")
    private String procedureValidatedSha256;
    /**
     * 规程成功校验时刻。
     */
    @Schema(description = "校验时刻")
    private Instant procedureValidatedAt;
    /**
     * 执行校验的管理员。
     */
    @Schema(description = "校验管理员")
    private String procedureValidatedBy;

    /**
     * 主键，由所属后端生成。
     */
    @Schema(description = "主键，由所属后端生成")
    private String id;

    /**
     * 本地 FK → kb_document_series。
     */
    @Schema(description = "本地 FK → kb_document_series")
    private String seriesId;

    /**
     * 上传者范围的客户端上传请求键，重试复用。
     */
    @Schema(description = "上传者范围的客户端上传请求键，重试复用")
    private String clientUploadId;

    /**
     * 系列内递增序号，锁系列后分配。
     */
    @Schema(description = "系列内递增序号，锁系列后分配")
    private Integer revisionNumber;

    /**
     * 真实文档标题。
     */
    @Schema(description = "真实文档标题")
    private String title;

    /**
     * PDF / MARKDOWN，创建后固定。
     */
    @Schema(description = "PDF / MARKDOWN，创建后固定")
    private String contentFormat;

    /**
     * 原文件名，仅展示，Markdown为UTF-8原件。
     */
    @Schema(description = "原文件名，仅展示，Markdown为UTF-8原件")
    private String originalFileName;

    /**
     * 正数，PDF至多20MiB、MARKDOWN至多1MiB。
     */
    @Schema(
            description = "正数，PDF至多20MiB、MARKDOWN至多1MiB",
            type = "string",
            pattern = "^(0|[1-9][0-9]*)$",
            example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long fileSizeBytes;

    /**
     * 流式摘要，同摘要的资料可有不同授权。
     */
    @Schema(description = "流式摘要，同摘要的资料可有不同授权")
    private String fileSha256;

    /**
     * 原始来源说明，不作为自动抓取地址。
     */
    @Schema(description = "原始来源说明，不作为自动抓取地址")
    private String sourceUrl;

    /**
     * 复核出处与使用依据，脱敏案例记录审批依据，发布必填。
     */
    @Schema(description = "复核出处与使用依据，脱敏案例记录审批依据，发布必填")
    private String sourceReviewNote;

    /**
     * 原文已知修订标识。
     */
    @Schema(description = "原文已知修订标识")
    private String revisionLabel;

    /**
     * 原文发布日期。
     */
    @Schema(description = "原文发布日期")
    private LocalDate issuedOn;

    /**
     * 人类可读适用条件摘要，实际匹配使用kb_document_applicability。
     */
    @Schema(description = "人类可读适用条件摘要，实际匹配使用kb_document_applicability")
    private String applicabilityNote;

    /**
     * DRAFT / PUBLISHED / DISABLED / RETIRED / WITHDRAWN / DELETED。
     */
    @Schema(description = "DRAFT / PUBLISHED / DISABLED / RETIRED / WITHDRAWN / DELETED")
    private String publicationStatus;

    /**
     * ALLOWED / REVOKED，访问权限独立于发布状态。
     */
    @Schema(description = "ALLOWED / REVOKED，访问权限独立于发布状态")
    private String accessStatus;

    /**
     * 最近访问权限变更原因。
     */
    @Schema(description = "最近访问权限变更原因")
    private String accessReason;

    /**
     * 风险撤回原因。
     */
    @Schema(description = "风险撤回原因")
    private String withdrawalReason;

    /**
     * 本地复合 FK(current_job_id,id) → kb_index_job(id,document_id)，外部 AI 任务使用同一 ID。
     */
    @Schema(
            description =
                    "本地复合 FK(current_job_id,id) → kb_index_job(id,document_id)，外部 AI 任务使用同一 ID")
    private String currentJobId;

    /**
     * 本地复合 FK(replaces_document_id,series_id) → kb_document(id,series_id)，发布替换时写入。
     */
    @Schema(
            description =
                    "本地复合 FK(replaces_document_id,series_id) → kb_document(id,series_id)，发布替换时写入")
    private String replacesDocumentId;

    /**
     * 首次成功发布时刻。
     */
    @Schema(description = "首次成功发布时刻")
    private Instant publishedAt;

    /**
     * 外部知识管理员账号。
     */
    @Schema(description = "外部知识管理员账号")
    private String publishedBy;

    /**
     * publication_status=DELETED 时必填。
     */
    @Schema(description = "publication_status=DELETED 时必填")
    private Instant deletedAt;

    /**
     * 外部上传人账号。
     */
    @Schema(description = "外部上传人账号")
    private String createdBy;

    /**
     * 记录创建时间。
     */
    @Schema(description = "记录创建时间")
    private Instant createdAt;

    /**
     * 更新时显式维护，默认值不自动随 UPDATE 改变。
     */
    @Schema(description = "更新时显式维护，默认值不自动随 UPDATE 改变")
    private Instant updatedAt;

    /**
     * 非负，更新比较旧值后加一。
     */
    @Schema(
            description = "非负，更新比较旧值后加一",
            type = "string",
            pattern = "^(0|[1-9][0-9]*)$",
            example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;

    /**
     * 当前草稿或固定发布规则，不把适用规则当成客户权限。
     */
    private List<DocumentRuleForm> rules;
}
