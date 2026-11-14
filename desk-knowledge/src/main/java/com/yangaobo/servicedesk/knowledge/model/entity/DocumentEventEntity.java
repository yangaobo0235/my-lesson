package com.yangaobo.servicedesk.knowledge.model.entity;

/**
 * 不可变资料动作审计。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@com.baomidou.mybatisplus.annotation.TableName(value = "kb_document_event", autoResultMap = true)
public class DocumentEventEntity implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键。 */
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    /** 本地 FK → kb_document。 */
    private String documentId;

    /** 外部知识管理员 ID。 */
    private String actorId;

    /** UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED / PUBLISHED / DISABLED / RESTORED / RETIRED / DELETED。 */
    private String eventType;

    /** 本地 FK → kb_document，替换对应的另一修订。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String relatedDocumentId;

    /** schema_version、动作原因、状态或适用规则差异；索引动作记录job_id，取消/重放另记command及operation_id。 */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode details;

    /** 动作提交时间。 */
    private java.time.LocalDateTime createdAt;
}
