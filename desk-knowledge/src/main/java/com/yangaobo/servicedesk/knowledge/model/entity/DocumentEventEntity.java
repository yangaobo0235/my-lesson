package com.yangaobo.servicedesk.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 不可变资料动作审计。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "kb_document_event", autoResultMap = true)
public class DocumentEventEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 本地 FK → kb_document。
     */
    private String documentId;

    /**
     * 外部知识管理员 ID。
     */
    private String actorId;

    /**
     * UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED /
     * PUBLISHED / DISABLED / RESTORED / RETIRED / DELETED。
     */
    private String eventType;

    /**
     * 本地 FK → kb_document，替换对应的另一修订。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String relatedDocumentId;

    /**
     * schema_version、动作原因、状态或适用规则差异；索引动作记录job_id，取消/重放另记command及operation_id。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode details;

    /**
     * 动作提交时间。
     */
    private LocalDateTime createdAt;
}
