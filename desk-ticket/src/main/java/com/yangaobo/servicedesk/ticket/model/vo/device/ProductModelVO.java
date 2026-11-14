package com.yangaobo.servicedesk.ticket.model.vo.device;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 产品型号的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ProductModelVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键，由所属后端生成。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键，由所属后端生成。")
    private String id;

    /** COMPUTER / PRINTER。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "COMPUTER / PRINTER。")
    private String category;

    /** 品牌，规范化后参与联合唯一。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "品牌，规范化后参与联合唯一。")
    private String brand;

    /** 型号，规范化后参与联合唯一。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "型号，规范化后参与联合唯一。")
    private String modelName;

    /** 已知系统适用说明，不猜测兼容性。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "已知系统适用说明，不猜测兼容性。")
    private String supportedSystems;

    /** ACTIVE / DISABLED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "ACTIVE / DISABLED。")
    private String status;

    /** 记录创建时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "记录创建时间。")
    private java.time.Instant createdAt;

    /** 更新时显式维护，默认值不自动随 UPDATE 改变。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "更新时显式维护，默认值不自动随 UPDATE 改变。")
    private java.time.Instant updatedAt;

    /** 非负，更新比较旧值后加一。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "非负，更新比较旧值后加一。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
