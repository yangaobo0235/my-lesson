package com.yangaobo.servicedesk.ticket.model.vo.onsite;

/**
 * 时段与实时剩余容量。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class OnsiteSlotVO implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 时段标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "时段标识")
    private String id;

    /** 已核对的服务区域代码。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "已核对的服务区域代码")
    private String serviceAreaCode;

    /** UTC到访窗口开始。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC到访窗口开始")
    private java.time.Instant startsAt;

    /** UTC到访窗口结束。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC到访窗口结束")
    private java.time.Instant endsAt;

    /** UTC开放时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC开放时间")
    private java.time.Instant bookingOpensAt;

    /** UTC申请截止。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC申请截止")
    private java.time.Instant bookingClosesAt;

    /** 人工确认的出勤容量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "人工确认的出勤容量")
    private Integer capacity;

    /** 已确认及履约占用。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "已确认及履约占用")
    private Integer reservedCount;

    /** DRAFT、PUBLISHED或CLOSED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "DRAFT、PUBLISHED或CLOSED")
    private String status;

    /** 发布时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "发布时刻")
    private java.time.Instant publishedAt;

    /** 关停时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "关停时刻")
    private java.time.Instant closedAt;

    /** 创建时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "创建时刻")
    private java.time.Instant createdAt;

    /** 更新时刻。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "更新时刻")
    private java.time.Instant updatedAt;

    /** 行版本。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "行版本")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer.class)
    private Long rowVersion;
}
