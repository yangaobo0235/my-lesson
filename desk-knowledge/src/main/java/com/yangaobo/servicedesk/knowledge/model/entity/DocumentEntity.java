package com.yangaobo.servicedesk.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 不可变原件修订与发布事实。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "kb_document", autoResultMap = true)
public class DocumentEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 本地 FK → kb_document_series。
     */
    private String seriesId;

    /**
     * 上传者范围的客户端上传请求键，重试复用。
     */
    private String clientUploadId;

    /**
     * 系列、规范化元数据及文件 SHA-256 的语义摘要。
     */
    private String uploadRequestHash;

    /**
     * 系列内递增序号，锁系列后分配。
     */
    private Integer revisionNumber;

    /**
     * 真实文档标题。
     */
    private String title;

    /**
     * PDF / MARKDOWN，创建后固定。
     */
    private String contentFormat;

    /**
     * 原文件名，仅展示，Markdown为UTF-8原件。
     */
    private String originalFileName;

    /**
     * 所属环境允许的 OSS 私有 Bucket，上传时固定。
     */
    private String storageBucket;

    /**
     * 服务端生成的 OSS Object Key，全局唯一且不复用，客户端不能指定。
     */
    private String storageKey;

    /**
     * 开启版本控制后返回的 OSS Version ID，精确读取及恢复使用，不接受空值或字面量 null。
     */
    private String storageVersionId;

    /**
     * 正数，PDF至多20MiB、MARKDOWN至多1MiB。
     */
    private Long fileSizeBytes;

    /**
     * 流式摘要，同摘要的资料可有不同授权。
     */
    private String fileSha256;

    /**
     * 原始来源说明，不作为自动抓取地址。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceUrl;

    /**
     * 复核出处与使用依据，脱敏案例记录审批依据，发布必填。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceReviewNote;

    /**
     * 原文已知修订标识。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revisionLabel;

    /**
     * 原文发布日期。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate issuedOn;

    /**
     * 人类可读适用条件摘要，实际匹配使用kb_document_applicability。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String applicabilityNote;

    /**
     * DRAFT / PUBLISHED / DISABLED / RETIRED / DELETED。
     */
    private String publicationStatus;

    /**
     * 本地复合 FK(current_job_id,id) → kb_index_job(id,document_id)，外部 AI 任务使用同一 ID。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentJobId;

    /**
     * 本地复合 FK(replaces_document_id,series_id) → kb_document(id,series_id)，发布替换时写入。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String replacesDocumentId;

    /**
     * 首次成功发布时刻。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishedAt;

    /**
     * 外部知识管理员账号。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String publishedBy;

    /**
     * publication_status=DELETED 时必填。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime deletedAt;

    /**
     * 外部上传人账号。
     */
    private String createdBy;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时显式维护，默认值不自动随 UPDATE 改变。
     */
    private LocalDateTime updatedAt;

    /**
     * 非负，更新比较旧值后加一。
     */
    private Long rowVersion;
}
