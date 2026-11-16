package com.yangaobo.servicedesk.ticket.model.vo.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 审计中的安全历史字段，未知字段不输出，不含任何口令。
 *
 * @author yangaobo
 */
@Data
public class ServiceAuditStateVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更时的id，不适用时为空。
     */
    @Schema(description = "变更时的id，不适用时为空。")
    private String id;

    /**
     * 变更时的customerId，不适用时为空。
     */
    @Schema(description = "变更时的customerId，不适用时为空。")
    private String customerId;

    /**
     * 变更时的serviceItemId，不适用时为空。
     */
    @Schema(description = "变更时的serviceItemId，不适用时为空。")
    private String serviceItemId;

    /**
     * 变更时的serviceCode，不适用时为空。
     */
    @Schema(description = "变更时的serviceCode，不适用时为空。")
    private String serviceCode;

    /**
     * 变更时的category，不适用时为空。
     */
    @Schema(description = "变更时的category，不适用时为空。")
    private String category;

    /**
     * 变更时的serviceName，不适用时为空。
     */
    @Schema(description = "变更时的serviceName，不适用时为空。")
    private String serviceName;

    /**
     * 变更时的supportScope，不适用时为空。
     */
    @Schema(description = "变更时的supportScope，不适用时为空。")
    private String supportScope;

    /**
     * 变更时的status，不适用时为空。
     */
    @Schema(description = "变更时的status，不适用时为空。")
    private String status;

    /**
     * 变更时的changeReason，不适用时为空。
     */
    @Schema(description = "变更时的changeReason，不适用时为空。")
    private String changeReason;

    /**
     * 变更时的rowVersion，不适用时为空。
     */
    @Schema(description = "变更时的rowVersion，不适用时为空。")
    private String rowVersion;

    /**
     * 变更时的startsOn，不适用时为空。
     */
    @Schema(description = "变更时的startsOn，不适用时为空。")
    private String startsOn;

    /**
     * 变更时的endsOn，不适用时为空。
     */
    @Schema(description = "变更时的endsOn，不适用时为空。")
    private String endsOn;

    /**
     * 变更时的createdAt，不适用时为空。
     */
    @Schema(description = "变更时的createdAt，不适用时为空。")
    private String createdAt;

    /**
     * 变更时的updatedAt，不适用时为空。
     */
    @Schema(description = "变更时的updatedAt，不适用时为空。")
    private String updatedAt;

    /**
     * 变更时的onsiteSupported，不适用时为空。
     */
    @Schema(description = "变更时的onsiteSupported，不适用时为空。")
    private Boolean onsiteSupported;
}
