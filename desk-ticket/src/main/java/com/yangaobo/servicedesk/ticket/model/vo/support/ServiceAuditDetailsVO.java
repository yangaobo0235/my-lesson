package com.yangaobo.servicedesk.ticket.model.vo.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 不可变审计的公开历史投影，明确区分变更前后。
 *
 * @author yangaobo
 */
@Data
public class ServiceAuditDetailsVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更前；创建时为空。
     */
    @Schema(description = "变更前；创建时为空。")
    private ServiceAuditStateVO before;

    /**
     * 变更后；不含内部配置。
     */
    @Schema(description = "变更后；不含内部配置。")
    private ServiceAuditStateVO after;

    /**
     * 操作者提供的变更原因。
     */
    @Schema(description = "操作者提供的变更原因。")
    private String reason;
}
