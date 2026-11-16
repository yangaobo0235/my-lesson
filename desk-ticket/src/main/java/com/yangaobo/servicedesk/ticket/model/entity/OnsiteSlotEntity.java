package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区域时段持久映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName("sd_onsite_slot")
public class OnsiteSlotEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 时段标识。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 已核对的服务区域代码。
     */
    private String serviceAreaCode;

    /**
     * UTC到访窗口开始。
     */
    private LocalDateTime startsAt;

    /**
     * UTC到访窗口结束。
     */
    private LocalDateTime endsAt;

    /**
     * UTC开放时间。
     */
    private LocalDateTime bookingOpensAt;

    /**
     * UTC申请截止。
     */
    private LocalDateTime bookingClosesAt;

    /**
     * 人工确认的出勤容量。
     */
    private Integer capacity;

    /**
     * 已确认及履约占用。
     */
    private Integer reservedCount;

    /**
     * DRAFT、PUBLISHED或CLOSED。
     */
    private String status;

    /**
     * 创建管理员。
     */
    private String createdBy;

    /**
     * 发布管理员。
     */
    private String publishedBy;

    /**
     * 关停管理员。
     */
    private String closedBy;

    /**
     * 发布时刻。
     */
    private LocalDateTime publishedAt;

    /**
     * 关停时刻。
     */
    private LocalDateTime closedAt;

    /**
     * 创建时刻。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时刻。
     */
    private LocalDateTime updatedAt;

    /**
     * 行版本。
     */
    private Long rowVersion;
}
