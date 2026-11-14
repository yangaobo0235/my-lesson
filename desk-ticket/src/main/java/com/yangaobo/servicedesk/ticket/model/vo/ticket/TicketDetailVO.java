package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工单详情与时间线的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketDetailVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 当前授权工单详情。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前授权工单详情。")
    private TicketVO ticket;

    /** 首批授权时间线，更多事件按分页读取。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "首批授权时间线，更多事件按分页读取。")
    private java.util.List<TicketEventVO> events;
}
