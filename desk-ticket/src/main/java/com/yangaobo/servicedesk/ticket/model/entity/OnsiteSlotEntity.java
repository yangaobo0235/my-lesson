package com.yangaobo.servicedesk.ticket.model.entity;

/**
 * 区域时段持久映射。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@com.baomidou.mybatisplus.annotation.TableName("sd_onsite_slot")
public class OnsiteSlotEntity implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 时段标识。 */
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    /** 已核对的服务区域代码。 */
    private String serviceAreaCode;

    /** UTC到访窗口开始。 */
    private java.time.LocalDateTime startsAt;

    /** UTC到访窗口结束。 */
    private java.time.LocalDateTime endsAt;

    /** UTC开放时间。 */
    private java.time.LocalDateTime bookingOpensAt;

    /** UTC申请截止。 */
    private java.time.LocalDateTime bookingClosesAt;

    /** 人工确认的出勤容量。 */
    private Integer capacity;

    /** 已确认及履约占用。 */
    private Integer reservedCount;

    /** DRAFT、PUBLISHED或CLOSED。 */
    private String status;

    /** 创建管理员。 */
    private String createdBy;

    /** 发布管理员。 */
    private String publishedBy;

    /** 关停管理员。 */
    private String closedBy;

    /** 发布时刻。 */
    private java.time.LocalDateTime publishedAt;

    /** 关停时刻。 */
    private java.time.LocalDateTime closedAt;

    /** 创建时刻。 */
    private java.time.LocalDateTime createdAt;

    /** 更新时刻。 */
    private java.time.LocalDateTime updatedAt;

    /** 行版本。 */
    private Long rowVersion;
}
