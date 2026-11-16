package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 不可变工单事件的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketEventVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @Schema(description = "主键，由所属后端生成。")
    private String id;

    /**
     * 本地 FK → sd_ticket。
     */
    @Schema(description = "本地 FK → sd_ticket。")
    private String ticketId;

    /**
     * 工单内正序，锁工单分配。
     */
    @Schema(description = "工单内正序，锁工单分配。")
    private Integer sequenceNumber;

    /**
     * CREATED / ACCEPTED / ASSIGNED / REASSIGNED / NEED_INFO / SUPPLEMENTED / RESOLVED / UNRESOLVED
     * / CLOSED / CANCELLED / COMMENTED / ACCEPTANCE_OVERDUE（旧RESPONSE_OVERDUE只读兼容） /
     * PRIORITY_CHANGED / ONSITE_ELIGIBILITY_CHANGED / ONSITE_BOOKED / ONSITE_BOOKING_CANCELLED /
     * ONSITE_FINISHED / CONTEXT_UPDATED。
     */
    private String eventType;

    /**
     * 上门确认、已确认预约取消或履约事件的本地复合 FK(onsite_booking_id,ticket_id) → sd_onsite_booking(id,ticket_id)。
     */
    @Schema(
            description =
                    "上门确认、已确认预约取消或履约事件的本地复合 FK(onsite_booking_id,ticket_id) → sd_onsite_booking(id,ticket_id)。")
    private String onsiteBookingId;

    /**
     * USER / SYSTEM。
     */
    @Schema(description = "USER / SYSTEM。")
    private String actorType;

    /**
     * USER 必填外部账号；SYSTEM 为空。
     */
    @Schema(description = "USER 必填外部账号；SYSTEM 为空。")
    private String actorId;

    /**
     * PUBLIC / INTERNAL。
     */
    @Schema(description = "PUBLIC / INTERNAL。")
    private String visibility;

    /**
     * CREATED 为空，其他为动作前状态。
     */
    @Schema(description = "CREATED 为空，其他为动作前状态。")
    private String fromStatus;

    /**
     * 动作后状态；普通备注前后相同。
     */
    @Schema(description = "动作后状态；普通备注前后相同。")
    private String toStatus;

    /**
     * 公开说明或内部备注，最多5000字符。
     */
    @Schema(description = "公开说明或内部备注，最多5000字符。")
    private String content;

    /**
     * schema_version、前后处理人/优先级、分类/环境变化及处理/结单原因等受控字段。
     */
    @Schema(description = "schema_version、前后处理人/优先级、分类/环境变化及处理/结单原因等受控字段。")
    private TicketMetadataVO metadata;

    /**
     * 记录创建时间。
     */
    @Schema(description = "记录创建时间。")
    private Instant createdAt;
}
