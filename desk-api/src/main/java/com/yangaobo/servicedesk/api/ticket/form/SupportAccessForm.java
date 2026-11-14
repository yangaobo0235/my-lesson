package com.yangaobo.servicedesk.api.ticket.form;

/**
 * 原身份与支持范围核验输入。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
public class SupportAccessForm extends com.yangaobo.servicedesk.api.user.form.VerifySessionForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** NEW_SELF_SERVICE或HISTORY。 */
    private String purpose;

    /** 目标服务项目。 */
    private String serviceItemId;

    /** 可选设备。 */
    private String deviceId;

    /** 可选历史工单。 */
    private String ticketId;

    /** 明确反馈的问题环境。 */
    private com.yangaobo.servicedesk.api.ticket.dto.SupportEnvironment environment;
}
