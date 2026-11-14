package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工单的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "sd_ticket", autoResultMap = true)
public class TicketEntity implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键，由所属后端生成。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 全局唯一后端编号。 */
    private String ticketNo;

    /** 外部客户组织 ID，同时参与本地设备归属复合 FK。 */
    private String customerId;

    /** 外部提交人 ID。 */
    private String requesterId;

    /** 提交人范围幂等键。 */
    private String clientRequestId;

    /** 规范化语义输入 SHA-256。 */
    private String requestHash;

    /** 外部 AI 草稿 ID，非空唯一。 */
    private String sourceDraftId;

    /** 本地FK→it_service_item；空表示待分类，只承诺分流。 */
    private String serviceItemId;

    /**
     * 本地复合FK(entitlement_id,customer_id,service_item_id)→it_service_entitlement(id,customer_id,service_item_id)；后端查资格生成。
     */
    private String entitlementId;

    /**
     * schema_version、admission_type=TRIAGE/COVERED、资格版本/日期及项目条件/现场能力；TRIAGE另存仅审计用途admission_entitlement_id。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode serviceScopeSnapshot;

    /** 本地复合FK(device_id,customer_id)→it_device(id,customer_id)。 */
    private String deviceId;

    /** 与device_id同空同有，保存资产/型号，不复制敏感序列号。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode deviceSnapshot;

    /** schema_version、固定环境字段及其来源，未知显式为空。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode supportContext;

    /** 客户确认的检查、现象及未决问题，不复制私有聊天/内部知识全文。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private com.fasterxml.jackson.databind.JsonNode handoverSnapshot;

    /** 工单现场区域，申请预约前必须存在。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serviceAreaCode;

    /** 客户确认的现场地点，独立于设备后续地点变化。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serviceLocation;

    /** 用户确认标题，非空白。 */
    private String title;

    /** 故障现象，最多5000字符。 */
    private String symptom;

    /** 已尝试操作，最多5000字符。 */
    private String attemptedActions;

    /** NORMAL / HIGH，客服动作调整。 */
    private String priority;

    /** SUBMITTED / PROCESSING / WAITING_CUSTOMER / RESOLVED / CLOSED / CANCELLED。 */
    private String status;

    /** 外部当前技术人员 ID，SUBMITTED 为空。 */
    private String assigneeId;

    /** 第一次接单或分派成功，不随转派覆盖。 */
    private java.time.LocalDateTime firstAcceptedAt;

    /** 创建时默认优先级计算，后续合法调整按原创建时刻重算。 */
    private java.time.LocalDateTime acceptanceDueAt;

    /** 首次受理超期事件登记时间。 */
    private java.time.LocalDateTime acceptanceOverdueRecordedAt;

    /** RESOLVED/CLOSED必填，RESTORED / GUIDANCE / REFERRED；仅迁移可LEGACY_UNKNOWN，退回清空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resolutionType;

    /** 本轮处理结论时间，未解决回退时清空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDateTime resolvedAt;

    /** RESOLVED 后72小时；回退清空，关闭保留。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDateTime confirmationDueAt;

    /** 客服或当前处理人确认需要现场服务，仅 PROCESSING、WAITING_CUSTOMER 可以为 true。 */
    private Boolean onsiteEligible;

    /** 当且仅当CLOSED必填，CUSTOMER_CONFIRMED / CONFIRMATION_TIMEOUT；仅迁移可LEGACY_UNKNOWN。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closureReason;

    /** CLOSED终态时有值，不代表客观维修成功。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDateTime closedAt;

    /** CANCELLED 终态时有值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDateTime cancelledAt;

    /** 记录创建时间。 */
    private java.time.LocalDateTime createdAt;

    /** 更新时显式维护，默认值不自动随 UPDATE 改变。 */
    private java.time.LocalDateTime updatedAt;

    /** 非负，更新比较旧值后加一。 */
    private Long rowVersion;
}
