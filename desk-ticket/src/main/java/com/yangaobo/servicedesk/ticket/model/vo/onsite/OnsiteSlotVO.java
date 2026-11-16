package com.yangaobo.servicedesk.ticket.model.vo.onsite;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时段与实时剩余容量。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class OnsiteSlotVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 时段标识。
     */
    @Schema(description = "时段标识")
    private String id;

    /**
     * 已核对的服务区域代码。
     */
    @Schema(description = "已核对的服务区域代码")
    private String serviceAreaCode;

    /**
     * UTC到访窗口开始。
     */
    @Schema(description = "UTC到访窗口开始")
    private Instant startsAt;

    /**
     * UTC到访窗口结束。
     */
    @Schema(description = "UTC到访窗口结束")
    private Instant endsAt;

    /**
     * UTC开放时间。
     */
    @Schema(description = "UTC开放时间")
    private Instant bookingOpensAt;

    /**
     * UTC申请截止。
     */
    @Schema(description = "UTC申请截止")
    private Instant bookingClosesAt;

    /**
     * 人工确认的出勤容量。
     */
    @Schema(description = "人工确认的出勤容量")
    private Integer capacity;

    /**
     * 已确认及履约占用。
     */
    @Schema(description = "已确认及履约占用")
    private Integer reservedCount;

    /**
     * DRAFT、PUBLISHED或CLOSED。
     */
    @Schema(description = "DRAFT、PUBLISHED或CLOSED")
    private String status;

    /**
     * 发布时刻。
     */
    @Schema(description = "发布时刻")
    private Instant publishedAt;

    /**
     * 关停时刻。
     */
    @Schema(description = "关停时刻")
    private Instant closedAt;

    /**
     * 创建时刻。
     */
    @Schema(description = "创建时刻")
    private Instant createdAt;

    /**
     * 更新时刻。
     */
    @Schema(description = "更新时刻")
    private Instant updatedAt;

    /**
     * 行版本。
     */
    @Schema(description = "行版本", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
