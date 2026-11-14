package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 受理时服务范围的安全历史投影。
 *
 * @author yangaobo
 */
@Data
public class ServiceScopeSnapshotVO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 受理类型COVERED或TRIAGE。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "受理类型COVERED或TRIAGE。")
    private String admissionType;

    /** 已分类受理资格。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "已分类受理资格。")
    private String entitlementId;

    /** 待分类受理的审计资格。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "待分类受理的审计资格。")
    private String admissionEntitlementId;

    /** 受理时资格版本字符串。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "受理时资格版本字符串。")
    private String qualificationVersion;

    /** 服务起始日期。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "服务起始日期。")
    private String startsOn;

    /** 服务结束日期。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "服务结束日期。")
    private String endsOn;

    /** 已分类服务项目。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "已分类服务项目。")
    private String serviceItemId;

    /** 受理支持条件。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "受理支持条件。")
    private String supportScope;

    /** 受理时是否支持现场服务。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "受理时是否支持现场服务。")
    private Boolean onsiteSupported;
}
