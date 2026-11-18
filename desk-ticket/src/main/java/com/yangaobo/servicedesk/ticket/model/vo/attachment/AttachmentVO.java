package com.yangaobo.servicedesk.ticket.model.vo.attachment;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

/**
 * 附件的安全元信息，不暴露存储地址或脱敏正文。
 *
 * @author yangaobo
 */
@Data
@Schema(description = "附件的安全元信息，不暴露存储地址或脱敏正文")
public class AttachmentVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件标识。
     */
    @Schema(description = "附件标识")
    private String id;

    /**
     * 所属工单。
     */
    @Schema(description = "所属工单")
    private String ticketId;

    /**
     * 原上传者。
     */
    @Schema(description = "原上传者")
    private String uploadedBy;

    /**
     * 上传者范围请求键。
     */
    @Schema(description = "上传者范围请求键")
    private String clientUploadId;

    /**
     * 仅用于展示的文件名。
     */
    @Schema(description = "仅用于展示的文件名")
    private String originalFileName;

    /**
     * TEXT、LOG 或 JSON。
     */
    @Schema(description = "TEXT、LOG 或 JSON")
    private String contentFormat;

    /**
     * PUBLIC 或 INTERNAL。
     */
    @Schema(description = "PUBLIC 或 INTERNAL")
    private String visibility;

    /**
     * 真实上传及访问状态。
     */
    @Schema(description = "真实上传及访问状态")
    private String status;

    /**
     * 原件字节数，最多 1MiB。
     */
    @Schema(description = "原件字节数，最多 1MiB")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long fileSizeBytes;

    /**
     * 原件 SHA-256。
     */
    @Schema(description = "原件 SHA-256")
    private String fileSha256;

    /**
     * 脱敏投影版本。
     */
    @Schema(description = "脱敏投影版本")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long redactionVersion;

    /**
     * 脱敏文本 SHA-256。
     */
    @Schema(description = "脱敏文本 SHA-256")
    private String redactedSha256;

    /**
     * 是否明确允许 AI 使用。
     */
    @Schema(description = "是否明确允许 AI 使用")
    private Boolean aiUseAllowed;

    /**
     * 脱敏确认人。
     */
    @Schema(description = "脱敏确认人")
    private String confirmedBy;

    /**
     * 确认 UTC 时刻。
     */
    @Schema(description = "确认 UTC 时刻")
    private LocalDateTime confirmedAt;

    /**
     * 逻辑撤销 UTC 时刻。
     */
    @Schema(description = "逻辑撤销 UTC 时刻")
    private LocalDateTime deletedAt;

    /**
     * 安全诊断码。
     */
    @Schema(description = "安全诊断码")
    private String errorCode;

    /**
     * 创建 UTC 时刻。
     */
    @Schema(description = "创建 UTC 时刻")
    private LocalDateTime createdAt;

    /**
     * 最近修改 UTC 时刻。
     */
    @Schema(description = "最近修改 UTC 时刻")
    private LocalDateTime updatedAt;

    /**
     * 附件独立写版本。
     */
    @Schema(description = "附件独立写版本")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
