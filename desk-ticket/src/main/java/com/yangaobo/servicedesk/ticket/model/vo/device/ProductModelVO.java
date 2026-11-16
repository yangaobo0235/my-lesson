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
 * 产品型号的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ProductModelVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @Schema(description = "主键，由所属后端生成。")
    private String id;

    /**
     * COMPUTER / PRINTER。
     */
    @Schema(description = "COMPUTER / PRINTER。")
    private String category;

    /**
     * 品牌，规范化后参与联合唯一。
     */
    @Schema(description = "品牌，规范化后参与联合唯一。")
    private String brand;

    /**
     * 型号，规范化后参与联合唯一。
     */
    @Schema(description = "型号，规范化后参与联合唯一。")
    private String modelName;

    /**
     * 已知系统适用说明，不猜测兼容性。
     */
    @Schema(description = "已知系统适用说明，不猜测兼容性。")
    private String supportedSystems;

    /**
     * ACTIVE / DISABLED。
     */
    @Schema(description = "ACTIVE / DISABLED。")
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
