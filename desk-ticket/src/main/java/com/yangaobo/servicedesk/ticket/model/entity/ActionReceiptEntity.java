package com.yangaobo.servicedesk.ticket.model.entity;

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
 * 与正式业务动作同事务保存的唯一不可变回执，不记录未知网络状态。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "sd_action_receipt", autoResultMap = true)
public class ActionReceiptEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 回执标识，由ticket生成。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 当前动作操作者。
     */
    private String actorId;

    /**
     * 操作者范围统一幂等键。
     */
    private String clientActionId;

    /**
     * 已登记的正式动作类型。
     */
    private String actionType;

    /**
     * 类型、目标、原版本及最终输入摘要。
     */
    private String requestHash;

    /**
     * 采用的不可变AI草稿修订，可空。
     */
    private String sourceDraftId;

    /**
     * 同草稿根全局唯一采用，可空。
     */
    private String sourceDraftRootId;

    /**
     * 正式工单标识。
     */
    private String ticketId;

    /**
     * 正式预约标识，可空。
     */
    private String bookingId;

    /**
     * APPLIED、SOLD_OUT或SLOT_CLOSED。
     */
    private String resultCode;

    /**
     * 不包含正文的原动作结果快照。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode resultSnapshot;

    /**
     * 本地事务提交的创建时刻。
     */
    private LocalDateTime createdAt;
}
