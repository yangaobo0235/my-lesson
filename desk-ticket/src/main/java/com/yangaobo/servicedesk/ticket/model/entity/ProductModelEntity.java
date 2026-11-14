package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 产品型号的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "it_product_model", autoResultMap = true)
public class ProductModelEntity implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键，由所属后端生成。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** COMPUTER / PRINTER。 */
    private String category;

    /** 品牌，规范化后参与联合唯一。 */
    private String brand;

    /** 型号，规范化后参与联合唯一。 */
    private String modelName;

    /** 已知系统适用说明，不猜测兼容性。 */
    private String supportedSystems;

    /** ACTIVE / DISABLED。 */
    private String status;

    /** 外部逻辑引用 → user 账号。 */
    private String createdBy;

    /** 记录创建时间。 */
    private java.time.LocalDateTime createdAt;

    /** 更新时显式维护，默认值不自动随 UPDATE 改变。 */
    private java.time.LocalDateTime updatedAt;

    /** 非负，更新比较旧值后加一。 */
    private Long rowVersion;
}
