package com.yangaobo.servicedesk.api.ticket.dto;

/**
 * 当前授权支持环境的最小投影。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class SupportContextDTO implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 固定客户。 */
    private String customerId;

    /** 服务项目，未分类可空。 */
    private String serviceItemId;

    /** 可选设备。 */
    private String deviceId;

    /** 可选型号。 */
    private String productModelId;

    /** 技术工单。 */
    private String ticketId;

    /** 受理范围。 */
    private String supportScope;

    /** 工单标题。 */
    private String title;

    /** 工单现象。 */
    private String symptom;

    /** 工单状态。 */
    private String status;

    /** 当前处理人。 */
    private String assigneeId;

    /** 技术工单输入版本。 */
    private Long ticketVersion;

    /** 有来源的问题环境。 */
    private SupportEnvironment environment;
}
