package com.yangaobo.servicedesk.knowledge.model.vo;

/**
 * 不可变资料动作审计的公开投影。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class DocumentEventVO implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键")
    private String id;

    /** 本地 FK → kb_document。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地 FK → kb_document")
    private String documentId;

    /** 外部知识管理员 ID。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "外部知识管理员 ID")
    private String actorId;

    /** UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED / PUBLISHED / DISABLED / RESTORED / RETIRED / DELETED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED / PUBLISHED / DISABLED / RESTORED / RETIRED / DELETED")
    private String eventType;

    /** 本地 FK → kb_document，替换对应的另一修订。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地 FK → kb_document，替换对应的另一修订")
    private String relatedDocumentId;

    /** schema_version、动作原因、状态或适用规则差异；索引动作记录job_id，取消/重放另记command及operation_id。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "schema_version、动作原因、状态或适用规则差异；索引动作记录job_id，取消/重放另记command及operation_id")
    private com.fasterxml.jackson.databind.JsonNode details;

    /** 动作提交时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "动作提交时间")
    private java.time.Instant createdAt;
}
