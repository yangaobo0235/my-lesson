package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 浏览器只能选择支持对象和反馈环境，身份由当前会话提供。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.EqualsAndHashCode(callSuper = true)
public class KnowledgeReadForm extends com.yangaobo.servicedesk.common.base.BasePageForm {
    @java.io.Serial private static final long serialVersionUID = 1L;
    /** 当前项目，工单和历史引用模式由后端决定。 */
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String serviceItemId;
    /** 可选本人客户设备。 */
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String deviceId;
    /** 有权工单引用，不接受任意客户ID。 */
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String ticketId;
    /** 本人历史运行引用，须与实际来源ID同时提供。 */
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String generationId;
    /** 本轮实际知识来源，不能用候选或任意jobId扩大范围。 */
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String sourceUsageId;
    /** 用户明确反馈的环境，不等于遥测实证。 */
    private com.yangaobo.servicedesk.api.ticket.dto.SupportEnvironment environment;
}
