package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 不可变工单事件的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "sd_ticket_event", autoResultMap = true)
public class TicketEventEntity implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键，由所属后端生成。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 本地 FK → sd_ticket。 */
    private String ticketId;

    /** 工单内正序，锁工单分配。 */
    private Integer sequenceNumber;

    /**
     * CREATED / ACCEPTED / ASSIGNED / REASSIGNED / NEED_INFO / SUPPLEMENTED / RESOLVED / UNRESOLVED
     * / CLOSED / CANCELLED / COMMENTED / ACCEPTANCE_OVERDUE（旧RESPONSE_OVERDUE只读兼容） /
     * PRIORITY_CHANGED / ONSITE_ELIGIBILITY_CHANGED / ONSITE_BOOKED / ONSITE_BOOKING_CANCELLED /
     * ONSITE_FINISHED / CONTEXT_UPDATED。
     */
    private String eventType;

    /** 上门确认、已确认预约取消或履约事件的本地复合 FK(onsite_booking_id,ticket_id) → sd_onsite_booking(id,ticket_id)。 */
    private String onsiteBookingId;

    /** USER / SYSTEM。 */
    private String actorType;

    /** USER 必填外部账号；SYSTEM 为空。 */
    private String actorId;

    /** 人工动作重试键，CREATED由工单创建键负责，SYSTEM为空。 */
    private String clientActionId;

    /** 外部ai_action_draft逻辑ID，非空唯一，仅人工记录/解决采用草稿时填写。 */
    private String sourceDraftId;

    /** 人工动作类型、期望版本及语义输入摘要，与 client_action_id 同空同有。 */
    private String actionRequestHash;

    /** PUBLIC / INTERNAL。 */
    private String visibility;

    /** CREATED 为空，其他为动作前状态。 */
    private String fromStatus;

    /** 动作后状态；普通备注前后相同。 */
    private String toStatus;

    /** 公开说明或内部备注，最多5000字符。 */
    private String content;

    /** schema_version、前后处理人/优先级、分类/环境变化及处理/结单原因等受控字段。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode metadata;

    /** 记录创建时间。 */
    private java.time.LocalDateTime createdAt;
}
