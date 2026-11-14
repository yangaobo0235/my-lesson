package com.yangaobo.servicedesk.knowledge.model.vo;

/**
 * 索引尝试及SUBMIT和CANCEL事务事实的公开投影。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class IndexJobVO implements java.io.Serializable {
    /** true为AI已持久受理，false仅表示本次可信NOT_FOUND，null表示尚未查询。 */
    private Boolean aiAccepted;
    /** AI独立执行状态，不与Java提交状态合并。 */
    private String aiStatus;
    /** AI实际执行阶段。 */
    private String aiStage;
    /** 原解析任务收敛状态。 */
    private String parserState;
    /** 当前双索引副本只读复核结果。 */
    private Boolean replicasVerified;
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键，knowledge生成，线合同为jobId，AI沿用同一任务ID。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键，knowledge生成，线合同为jobId，AI沿用同一任务ID")
    private String id;

    /** 本地FK→kb_document。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地FK→kb_document")
    private String documentId;

    /** 文档内递增正序尝试号，准备失败也保留历史。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "文档内递增正序尝试号，准备失败也保留历史")
    private Integer attemptNumber;

    /** 初始发起知识管理员。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "初始发起知识管理员")
    private String requestedBy;

    /** 发起人的索引提交幂等键，同键沿用原jobId。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "发起人的索引提交幂等键，同键沿用原jobId")
    private String clientRequestId;

    /** 与MQ/RPC核对的不可变输入摘要。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "与MQ/RPC核对的不可变输入摘要")
    private String requestHash;

    /** PREPARING / COMMITTED / ABORTED，后两者终态。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "PREPARING / COMMITTED / ABORTED，后两者终态")
    private String submitState;

    /** 本地提交准备截止，首次登记后固定，初值30秒。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本地提交准备截止，首次登记后固定，初值30秒")
    private java.time.Instant submitDeadlineAt;

    /** 当且仅当submit_state=COMMITTED必填，固定本地提交事实。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当且仅当submit_state=COMMITTED必填，固定本地提交事实")
    private java.time.Instant submitCommittedAt;

    /** 当前取消操作ID；取消重试仅在前次ABORTED后分配新值。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前取消操作ID；取消重试仅在前次ABORTED后分配新值")
    private String cancelOperationId;

    /** 当前取消准备的知识管理员，历史由资料事件保留。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前取消准备的知识管理员，历史由资料事件保留")
    private String cancelRequestedBy;

    /** NONE / PREPARING / COMMITTED / ABORTED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "NONE / PREPARING / COMMITTED / ABORTED")
    private String cancelState;

    /** 非NONE时必填，当前取消准备截止。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "非NONE时必填，当前取消准备截止")
    private java.time.Instant cancelDeadlineAt;

    /** 当且仅当cancel_state=COMMITTED必填，提交后固定。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当且仅当cancel_state=COMMITTED必填，提交后固定")
    private java.time.Instant cancelCommittedAt;

    /** 当前准备/失败的安全诊断，不作为事务提交证明。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前准备/失败的安全诊断，不作为事务提交证明")
    private String lastErrorCode;

    /** 首次登记准备事实。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "首次登记准备事实")
    private java.time.Instant createdAt;

    /** 业务状态更新时显式维护。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "业务状态更新时显式维护")
    private java.time.Instant updatedAt;
}
