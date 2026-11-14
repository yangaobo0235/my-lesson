package com.yangaobo.servicedesk.knowledge.model.entity;

/**
 * 索引尝试及SUBMIT和CANCEL事务事实。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@com.baomidou.mybatisplus.annotation.TableName(value = "kb_index_job", autoResultMap = true)
public class IndexJobEntity implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键，knowledge生成，线合同为jobId，AI沿用同一任务ID。 */
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    /** 本地FK→kb_document。 */
    private String documentId;

    /** 文档内递增正序尝试号，准备失败也保留历史。 */
    private Integer attemptNumber;

    /** 初始发起知识管理员。 */
    private String requestedBy;

    /** 发起人的索引提交幂等键，同键沿用原jobId。 */
    private String clientRequestId;

    /** schema_version、client_request原add输入及command_input不可变信封输入，不含传输事务ID/凭据。 */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode requestPayload;

    /** 与MQ/RPC核对的不可变输入摘要。 */
    private String requestHash;

    /** PREPARING / COMMITTED / ABORTED，后两者终态。 */
    private String submitState;

    /** 本地提交准备截止，首次登记后固定，初值30秒。 */
    private java.time.LocalDateTime submitDeadlineAt;

    /** 当且仅当submit_state=COMMITTED必填，固定本地提交事实。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime submitCommittedAt;

    /** 当前取消操作ID；取消重试仅在前次ABORTED后分配新值。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String cancelOperationId;

    /** 当前取消准备的知识管理员，历史由资料事件保留。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String cancelRequestedBy;

    /** NONE / PREPARING / COMMITTED / ABORTED。 */
    private String cancelState;

    /** 非NONE时必填，当前取消准备截止。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime cancelDeadlineAt;

    /** 当且仅当cancel_state=COMMITTED必填，提交后固定。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime cancelCommittedAt;

    /** 初始提交的schema_version及W3C上下文，重放不参与摘要。 */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode traceContext;

    /** 当前准备/失败的安全诊断，不作为事务提交证明。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String lastErrorCode;

    /** 首次登记准备事实。 */
    private java.time.LocalDateTime createdAt;

    /** 业务状态更新时显式维护。 */
    private java.time.LocalDateTime updatedAt;
}
