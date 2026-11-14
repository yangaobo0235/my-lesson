package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工单事件可公开的结构化变更，不直出持久化快照。
 *
 * @author yangaobo
 */
@Data
public class TicketMetadataVO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 新处理人。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "新处理人。")
    private String assigneeId;

    /** 原处理人。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原处理人。")
    private String previousAssigneeId;

    /** 原优先级。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原优先级。")
    private String oldPriority;

    /** 新优先级。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "新优先级。")
    private String newPriority;

    /** 本轮人工结果。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本轮人工结果。")
    private String resolutionType;

    /** 退回前人工结果。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "退回前人工结果。")
    private String previousResolutionType;

    /** 转介去向。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "转介去向。")
    private String referralTarget;

    /** 结单原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "结单原因。")
    private String closureReason;

    /** 变更前受理范围。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "变更前受理范围。")
    private ServiceScopeSnapshotVO oldScope;

    /** 变更后受理范围。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "变更后受理范围。")
    private ServiceScopeSnapshotVO newScope;

    /** 变更前环境。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "变更前环境。")
    private SupportContextVO oldContext;

    /** 变更后环境。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "变更后环境。")
    private SupportContextVO newContext;
}
