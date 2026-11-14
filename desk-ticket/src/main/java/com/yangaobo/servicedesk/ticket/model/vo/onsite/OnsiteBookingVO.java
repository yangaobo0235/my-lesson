package com.yangaobo.servicedesk.ticket.model.vo.onsite;

/**
 * 预约真实状态及地址快照。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class OnsiteBookingVO implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 预约标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "预约标识")
    private String id;

    /** 所属工单。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "所属工单")
    private String ticketId;

    /** 选定时段。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "选定时段")
    private String slotId;

    /** 申请时区域。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "申请时区域")
    private String serviceAreaCode;

    /** 申请时地址快照。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "申请时地址快照")
    private String visitAddress;

    /** 原申请键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原申请键")
    private String clientRequestId;

    /** CONFIRMED、REJECTED、CANCELLED或FINISHED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "CONFIRMED、REJECTED、CANCELLED或FINISHED")
    private String status;

    /** 拒绝原因，仅REJECTED非空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "拒绝原因，仅REJECTED非空")
    private String resultCode;

    /** 实际履约结果，仅FINISHED非空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "实际履约结果，仅FINISHED非空")
    private String finishResult;

    /** 确认时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "确认时刻")
    private java.time.Instant confirmedAt;

    /** 结束时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "结束时刻")
    private java.time.Instant endedAt;

    /** 申请决策时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "申请决策时刻")
    private java.time.Instant createdAt;

    /** 更新时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "更新时刻")
    private java.time.Instant updatedAt;
}
