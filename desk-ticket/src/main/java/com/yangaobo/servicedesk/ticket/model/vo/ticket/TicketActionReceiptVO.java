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
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long sequenceNumber;

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

    /**
     * 正式动作回执标识。
     */
    @Schema(description = "正式动作回执标识")
    private String receiptId;

    /**
     * 正式动作类型。
     */
    @Schema(description = "正式动作类型")
    private String actionType;

    /**
     * 原操作者动作键。
     */
    @Schema(description = "原操作者动作键")
    private String clientActionId;

    /**
     * 预约标识，可空。
     */
    @Schema(description = "预约标识，可空")
    private String bookingId;

    /**
     * 提交时预约状态，可空。
     */
    @Schema(description = "提交时预约状态，可空")
    private String bookingStatus;

    /**
     * 提交时预约版本，可空。
     */
    @Schema(description = "提交时预约版本，可空")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long bookingRowVersion;

    /**
     * 提交时工单写版本。
     */
    @Schema(description = "提交时工单写版本")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long ticketRowVersion;

    /**
     * APPLIED或确定预约拒绝原因。
     */
    @Schema(description = "APPLIED或确定预约拒绝原因")
    private String resultCode;

    /**
     * 原草稿修订，可空。
     */
    @Schema(description = "原草稿修订，可空")
    private String sourceDraftId;

    /**
     * 唯一采用的草稿根，可空。
     */
    @Schema(description = "唯一采用的草稿根，可空")
    private String sourceDraftRootId;

    /**
     * 原动作附件，可空。
     */
    @Schema(description = "原动作附件，可空")
    private String attachmentId;
    /**
     * 提交时附件状态。
     */
    @Schema(description = "提交时附件状态")
    private String attachmentStatus;
    /**
     * 提交时附件行版本。
     */
    @Schema(description = "提交时附件行版本")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long attachmentRowVersion;
    /**
     * 提交时脱敏版本。
     */
    @Schema(description = "提交时脱敏版本")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long redactionVersion;
    /**
     * 提交时 AI 使用授权。
     */
    @Schema(description = "提交时 AI 使用授权")
    private Boolean aiUseAllowed;
}
