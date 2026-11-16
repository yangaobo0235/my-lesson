package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单工作台聚合的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待受理数量。
     */
    @Schema(description = "待受理数量。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long submitted;

    /**
     * 处理中数量。
     */
    @Schema(description = "处理中数量。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long processing;

    /**
     * 待客户补充数量。
     */
    @Schema(description = "待客户补充数量。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long waitingCustomer;

    /**
     * 待客户确认数量。
     */
    @Schema(description = "待客户确认数量。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long resolved;

    /**
     * 未受理超期数量。
     */
    @Schema(description = "未受理超期数量。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long overdue;

    /**
     * 待分类数量。
     */
    @Schema(description = "待分类数量。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long triage;

    /**
     * 客户确认关闭数量。
     */
    @Schema(
            description = "客户确认关闭数量。",
            type = "string",
            pattern = "^(0|[1-9][0-9]*)$",
            example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long customerConfirmed;

    /**
     * 超时关闭数量。
     */
    @Schema(description = "超时关闭数量。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long confirmationTimeout;
}
