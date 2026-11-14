package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工单工作台聚合的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketOverviewVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 待受理数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "待受理数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long submitted;

    /** 处理中数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "处理中数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long processing;

    /** 待客户补充数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "待客户补充数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long waitingCustomer;

    /** 待客户确认数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "待客户确认数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long resolved;

    /** 未受理超期数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "未受理超期数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long overdue;

    /** 待分类数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "待分类数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long triage;

    /** 客户确认关闭数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "客户确认关闭数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long customerConfirmed;

    /** 超时关闭数量。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "超时关闭数量。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long confirmationTimeout;
}
