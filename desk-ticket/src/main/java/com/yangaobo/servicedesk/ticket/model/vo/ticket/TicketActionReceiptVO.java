package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人工动作原始回执的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketActionReceiptVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原动作事件标识。
     */
    @Schema(description = "原动作事件标识。")
    private String eventId;

    /**
     * 工单时间线序号。
     */
    @Schema(description = "工单时间线序号。")
    private Integer sequenceNumber;

    /**
     * 动作提交时的状态。
     */
    @Schema(description = "动作提交时的状态。")
    private String toStatus;

    /**
     * 所属工单。
     */
    @Schema(description = "所属工单。")
    private String ticketId;
}
