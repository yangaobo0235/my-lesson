package com.yangaobo.servicedesk.knowledge.model.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 不可变资料动作审计的公开投影。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class DocumentEventVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @Schema(description = "主键")
    private String id;

    /**
     * 本地 FK → kb_document。
     */
    @Schema(description = "本地 FK → kb_document")
    private String documentId;

    /**
     * 外部知识管理员 ID。
     */
    @Schema(description = "外部知识管理员 ID")
    private String actorId;

    /**
     * UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED /
     * PUBLISHED / DISABLED / RESTORED / RETIRED / WITHDRAWN / ACCESS_REVOKED / ACCESS_RESTORED / DELETED。
     */
    @Schema(
            description =
                    "UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED / PUBLISHED / DISABLED / RESTORED / RETIRED / WITHDRAWN / ACCESS_REVOKED / ACCESS_RESTORED / DELETED")
    private String eventType;

    /**
     * 本地 FK → kb_document，替换对应的另一修订。
     */
    @Schema(description = "本地 FK → kb_document，替换对应的另一修订")
    private String relatedDocumentId;

    /**
     * schema_version、动作原因、状态或适用规则差异；索引动作记录job_id，取消/重放另记command及operation_id。
     */
    @Schema(description = "schema_version、动作原因、状态或适用规则差异；索引动作记录job_id，取消/重放另记command及operation_id")
    private JsonNode details;

    /**
     * 动作提交时间。
     */
    @Schema(description = "动作提交时间")
    private Instant createdAt;
}
