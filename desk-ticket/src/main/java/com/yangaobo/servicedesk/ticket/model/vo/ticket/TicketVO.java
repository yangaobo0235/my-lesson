package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @Schema(description = "主键，由所属后端生成。")
    private String id;

    /**
     * 全局唯一后端编号。
     */
    @Schema(description = "全局唯一后端编号。")
    private String ticketNo;

    /**
     * 外部客户组织 ID，同时参与本地设备归属复合 FK。
     */
    @Schema(description = "外部客户组织 ID，同时参与本地设备归属复合 FK。")
    private String customerId;

    /**
     * 外部提交人 ID。
     */
    @Schema(description = "外部提交人 ID。")
    private String requesterId;

    /**
     * 本地FK→it_service_item；空表示待分类，只承诺分流。
     */
    @Schema(description = "本地FK→it_service_item；空表示待分类，只承诺分流。")
    private String serviceItemId;

    /**
     * 本地复合FK(entitlement_id,customer_id,service_item_id)→it_service_entitlement(id,customer_id,service_item_id)；后端查资格生成。
     */
    private String entitlementId;

    /**
     * schema_version、admission_type=TRIAGE/COVERED、资格版本/日期及项目条件/现场能力；TRIAGE另存仅审计用途admission_entitlement_id。
     */
    private ServiceScopeSnapshotVO serviceScopeSnapshot;

    /**
     * 本地复合FK(device_id,customer_id)→it_device(id,customer_id)。
     */
    @Schema(description = "本地复合FK(device_id,customer_id)→it_device(id,customer_id)。")
    private String deviceId;

    /**
     * 与device_id同空同有，保存资产/型号，不复制敏感序列号。
     */
    @Schema(description = "与device_id同空同有，保存资产/型号，不复制敏感序列号。")
    private DeviceSnapshotVO deviceSnapshot;

    /**
     * schema_version、固定环境字段及其来源，未知显式为空。
     */
    @Schema(description = "schema_version、固定环境字段及其来源，未知显式为空。")
    private SupportContextVO supportContext;

    /**
     * 客户确认的检查、现象及未决问题，不复制私有聊天/内部知识全文。
     */
    @Schema(description = "客户确认的检查、现象及未决问题，不复制私有聊天/内部知识全文。")
    private JsonNode handoverSnapshot;

    /**
     * 工单现场区域，申请预约前必须存在。
     */
    @Schema(description = "工单现场区域，申请预约前必须存在。")
    private String serviceAreaCode;

    /**
     * 客户确认的现场地点，独立于设备后续地点变化。
     */
    @Schema(description = "客户确认的现场地点，独立于设备后续地点变化。")
    private String serviceLocation;

    /**
     * 用户确认标题，非空白。
     */
    @Schema(description = "用户确认标题，非空白。")
    private String title;

    /**
     * 故障现象，最多5000字符。
     */
    @Schema(description = "故障现象，最多5000字符。")
    private String symptom;

    /**
     * 已尝试操作，最多5000字符。
     */
    @Schema(description = "已尝试操作，最多5000字符。")
    private String attemptedActions;

    /**
     * NORMAL / HIGH，客服动作调整。
     */
    @Schema(description = "NORMAL / HIGH，客服动作调整。")
    private String priority;

    /**
     * SUBMITTED / PROCESSING / WAITING_CUSTOMER / RESOLVED / CLOSED / CANCELLED。
     */
    @Schema(
            description =
                    "SUBMITTED / PROCESSING / WAITING_CUSTOMER / RESOLVED / CLOSED / CANCELLED。")
    private String status;

    /**
     * 外部当前技术人员 ID，SUBMITTED 为空。
     */
    @Schema(description = "外部当前技术人员 ID，SUBMITTED 为空。")
    private String assigneeId;

    /**
     * 第一次接单或分派成功，不随转派覆盖。
     */
    @Schema(description = "第一次接单或分派成功，不随转派覆盖。")
    private Instant firstAcceptedAt;

    /**
     * 创建时默认优先级计算，后续合法调整按原创建时刻重算。
     */
    @Schema(description = "创建时默认优先级计算，后续合法调整按原创建时刻重算。")
    private Instant acceptanceDueAt;

    /**
     * 首次受理超期事件登记时间。
     */
    @Schema(description = "首次受理超期事件登记时间。")
    private Instant acceptanceOverdueRecordedAt;

    /**
     * RESOLVED/CLOSED必填，RESTORED / GUIDANCE / REFERRED；仅迁移可LEGACY_UNKNOWN，退回清空。
     */
    @Schema(
            description =
                    "RESOLVED/CLOSED必填，RESTORED / GUIDANCE / REFERRED；仅迁移可LEGACY_UNKNOWN，退回清空。")
    private String resolutionType;

    /**
     * 本轮处理结论时间，未解决回退时清空。
     */
    @Schema(description = "本轮处理结论时间，未解决回退时清空。")
    private Instant resolvedAt;

    /**
     * RESOLVED 后72小时；回退清空，关闭保留。
     */
    @Schema(description = "RESOLVED 后72小时；回退清空，关闭保留。")
    private Instant confirmationDueAt;

    /**
     * 客服或当前处理人确认需要现场服务，仅 PROCESSING、WAITING_CUSTOMER 可以为 true。
     */
    @Schema(description = "客服或当前处理人确认需要现场服务，仅 PROCESSING、WAITING_CUSTOMER 可以为 true。")
    private Boolean onsiteEligible;

    /**
     * 当且仅当CLOSED必填，CUSTOMER_CONFIRMED / CONFIRMATION_TIMEOUT；仅迁移可LEGACY_UNKNOWN。
     */
    @Schema(
            description =
                    "当且仅当CLOSED必填，CUSTOMER_CONFIRMED / CONFIRMATION_TIMEOUT；仅迁移可LEGACY_UNKNOWN。")
    private String closureReason;

    /**
     * CLOSED终态时有值，不代表客观维修成功。
     */
    @Schema(description = "CLOSED终态时有值，不代表客观维修成功。")
    private Instant closedAt;

    /**
     * CANCELLED 终态时有值。
     */
    @Schema(description = "CANCELLED 终态时有值。")
    private Instant cancelledAt;

    /**
     * 记录创建时间。
     */
    @Schema(description = "记录创建时间。")
    private Instant createdAt;

    /**
     * 更新时显式维护，默认值不自动随 UPDATE 改变。
     */
    @Schema(description = "更新时显式维护，默认值不自动随 UPDATE 改变。")
    private Instant updatedAt;

    /**
     * 非负，更新比较旧值后加一。
     */
    @Schema(
            description = "非负，更新比较旧值后加一。",
            type = "string",
            pattern = "^(0|[1-9][0-9]*)$",
            example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
