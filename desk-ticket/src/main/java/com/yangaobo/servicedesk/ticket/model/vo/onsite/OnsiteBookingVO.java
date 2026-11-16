package com.yangaobo.servicedesk.ticket.model.vo.onsite;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预约真实状态及地址快照。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OnsiteBookingVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预约标识。
     */
    @Schema(description = "预约标识")
    private String id;

    /**
     * 所属工单。
     */
    @Schema(description = "所属工单")
    private String ticketId;

    /**
     * 选定时段。
     */
    @Schema(description = "选定时段")
    private String slotId;

    /**
     * 申请时区域。
     */
    @Schema(description = "申请时区域")
    private String serviceAreaCode;

    /**
     * 申请时地址快照。
     */
    @Schema(description = "申请时地址快照")
    private String visitAddress;

    /**
     * 原申请键。
     */
    @Schema(description = "原申请键")
    private String clientRequestId;

    /**
     * CONFIRMED、REJECTED、CANCELLED或FINISHED。
     */
    @Schema(description = "CONFIRMED、REJECTED、CANCELLED或FINISHED")
    private String status;

    /**
     * 拒绝原因，仅REJECTED非空。
     */
    @Schema(description = "拒绝原因，仅REJECTED非空")
    private String resultCode;

    /**
     * 实际履约结果，仅FINISHED非空。
     */
    @Schema(description = "实际履约结果，仅FINISHED非空")
    private String finishResult;

    /**
     * 确认时刻。
     */
    @Schema(description = "确认时刻")
    private Instant confirmedAt;

    /**
     * 结束时刻。
     */
    @Schema(description = "结束时刻")
    private Instant endedAt;

    /**
     * 申请决策时刻。
     */
    @Schema(description = "申请决策时刻")
    private Instant createdAt;

    /**
     * 更新时刻。
     */
    @Schema(description = "更新时刻")
    private Instant updatedAt;
}
