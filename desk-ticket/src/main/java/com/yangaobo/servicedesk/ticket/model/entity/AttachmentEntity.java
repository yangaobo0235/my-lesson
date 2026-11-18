package com.yangaobo.servicedesk.ticket.model.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 工单诊断附件意图、原件版本及脱敏授权事实。
 *
 * @author yangaobo
 */
@Data
@TableName("sd_attachment")
public class AttachmentEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件标识。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 所属工单。
     */
    private String ticketId;

    /**
     * 原上传者。
     */
    private String uploadedBy;

    /**
     * 上传者范围请求键。
     */
    private String clientUploadId;

    /**
     * 原上传输入摘要。
     */
    private String requestHash;

    /**
     * 仅用于展示的文件名。
     */
    private String originalFileName;

    /**
     * TEXT、LOG 或 JSON。
     */
    private String contentFormat;

    /**
     * PUBLIC 或 INTERNAL。
     */
    private String visibility;

    /**
     * 真实上传及访问状态。
     */
    private String status;

    /**
     * 本服务私有桶。
     */
    private String storageBucket;

    /**
     * 上传前登记且不复用的对象键。
     */
    private String storageKey;

    /**
     * 已核验的精确对象版本。
     */
    private String storageVersionId;

    /**
     * 原件字节数，最多 1MiB。
     */
    private Long fileSizeBytes;

    /**
     * 原件 SHA-256。
     */
    private String fileSha256;

    /**
     * 上传者确认的脱敏文本。
     */
    private String redactedText;

    /**
     * 脱敏投影版本。
     */
    private Long redactionVersion;

    /**
     * 脱敏文本 SHA-256。
     */
    private String redactedSha256;

    /**
     * 是否明确允许 AI 使用。
     */
    private Boolean aiUseAllowed;

    /**
     * 脱敏确认人。
     */
    private String confirmedBy;

    /**
     * 确认 UTC 时刻。
     */
    private LocalDateTime confirmedAt;

    /**
     * 获批的保留期限，空表示不得清理。
     */
    private LocalDateTime retainUntil;

    /**
     * 逻辑撤销 UTC 时刻。
     */
    private LocalDateTime deletedAt;

    /**
     * 安全诊断码。
     */
    private String errorCode;

    /**
     * 创建 UTC 时刻。
     */
    private LocalDateTime createdAt;

    /**
     * 最近修改 UTC 时刻。
     */
    private LocalDateTime updatedAt;

    /**
     * 附件独立写版本。
     */
    private Long rowVersion;
}
