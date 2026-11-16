package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.annotation.SchemaEnum;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.ticket.model.enums.TicketStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 有权工单分页筛选的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketQueryForm extends BasePageForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工单状态过滤。
     */
    @Schema(description = "工单状态过滤。")
    @SchemaEnum(TicketStatusEnum.class)
    @Pattern(regexp = "SUBMITTED|PROCESSING|WAITING_CUSTOMER|RESOLVED|CLOSED|CANCELLED")
    private String status;

    /**
     * 只查询待分类。
     */
    @Schema(description = "只查询待分类。")
    private Boolean triage;

    /**
     * 只查询当前处理人为本人。
     */
    @Schema(description = "只查询当前处理人为本人。")
    private Boolean assignedToMe;

    /**
     * 只查询未受理且已超期。
     */
    @Schema(description = "只查询未受理且已超期。")
    private Boolean overdue;
}
