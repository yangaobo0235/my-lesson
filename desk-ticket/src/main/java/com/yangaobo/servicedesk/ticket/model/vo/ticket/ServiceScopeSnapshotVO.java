package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 受理时服务范围的安全历史投影。
 *
 * @author yangaobo
 */
@Data
public class ServiceScopeSnapshotVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 受理类型COVERED或TRIAGE。
     */
    @Schema(description = "受理类型COVERED或TRIAGE。")
    private String admissionType;

    /**
     * 已分类受理资格。
     */
    @Schema(description = "已分类受理资格。")
    private String entitlementId;

    /**
     * 待分类受理的审计资格。
     */
    @Schema(description = "待分类受理的审计资格。")
    private String admissionEntitlementId;

    /**
     * 受理时资格版本字符串。
     */
    @Schema(description = "受理时资格版本字符串。")
    private String qualificationVersion;

    /**
     * 服务起始日期。
     */
    @Schema(description = "服务起始日期。")
    private String startsOn;

    /**
     * 服务结束日期。
     */
    @Schema(description = "服务结束日期。")
    private String endsOn;

    /**
     * 已分类服务项目。
     */
    @Schema(description = "已分类服务项目。")
    private String serviceItemId;

    /**
     * 受理支持条件。
     */
    @Schema(description = "受理支持条件。")
    private String supportScope;

    /**
     * 受理时是否支持现场服务。
     */
    @Schema(description = "受理时是否支持现场服务。")
    private Boolean onsiteSupported;
}
