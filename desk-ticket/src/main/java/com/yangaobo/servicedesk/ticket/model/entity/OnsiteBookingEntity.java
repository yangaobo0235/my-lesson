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
 * 预约及原请求回执。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName("sd_onsite_booking")
public class OnsiteBookingEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预约标识。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 所属工单。
     */
    private String ticketId;

    /**
     * 选定时段。
     */
    private String slotId;

    /**
     * 申请时区域。
     */
    private String serviceAreaCode;

    /**
     * 申请时地址快照。
     */
    private String visitAddress;

    /**
     * 原申请键。
     */
    private String clientRequestId;

    /**
     * 原申请语义摘要。
     */
    private String requestHash;

    /**
     * CONFIRMED、REJECTED、CANCELLED或FINISHED。
     */
    private String status;

    /**
     * 拒绝原因，仅REJECTED非空。
     */
    private String resultCode;

    /**
     * 实际履约结果，仅FINISHED非空。
     */
    private String finishResult;

    /**
     * 确认时刻。
     */
    private LocalDateTime confirmedAt;

    /**
     * 结束时刻。
     */
    private LocalDateTime endedAt;

    /**
     * 申请决策时刻。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时刻。
     */
    private LocalDateTime updatedAt;
}
