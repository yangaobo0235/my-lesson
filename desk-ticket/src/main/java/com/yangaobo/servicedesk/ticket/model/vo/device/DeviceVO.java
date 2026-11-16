package com.yangaobo.servicedesk.ticket.model.vo.device;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户可选资产的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class DeviceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @Schema(description = "主键，由所属后端生成。")
    private String id;

    /**
     * 外部逻辑引用 → 客户组织。
     */
    @Schema(description = "外部逻辑引用 → 客户组织。")
    private String customerId;

    /**
     * 本地 FK → it_product_model。
     */
    @Schema(description = "本地 FK → it_product_model。")
    private String productModelId;

    /**
     * 客户内规范化资产编号。
     */
    @Schema(description = "客户内规范化资产编号。")
    private String assetCode;

    /**
     * 序列号，敏感展示按权限裁剪。
     */
    @Schema(description = "序列号，敏感展示按权限裁剪。")
    private String serialNumber;

    /**
     * 使用地点，不作权限依据。
     */
    @Schema(description = "使用地点，不作权限依据。")
    private String location;

    /**
     * 设备默认区域代码，规范大写ASCII，仅供提单预填；预约校验工单区域/地点。
     */
    @Schema(description = "设备默认区域代码，规范大写ASCII，仅供提单预填；预约校验工单区域/地点。")
    private String serviceAreaCode;

    /**
     * ACTIVE / RETIRED。
     */
    @Schema(description = "ACTIVE / RETIRED。")
    private String status;

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
