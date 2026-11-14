package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.base.BasePageForm;

import jakarta.validation.constraints.Pattern;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 有权工单分页筛选的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketQueryForm extends BasePageForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 工单状态过滤。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "工单状态过滤。")
    @Pattern(regexp = "SUBMITTED|PROCESSING|WAITING_CUSTOMER|RESOLVED|CLOSED|CANCELLED")
    private String status;

    /** 只查询待分类。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "只查询待分类。")
    private Boolean triage;

    /** 只查询当前处理人为本人。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "只查询当前处理人为本人。")
    private Boolean assignedToMe;

    /** 只查询未受理且已超期。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "只查询未受理且已超期。")
    private Boolean overdue;
}
