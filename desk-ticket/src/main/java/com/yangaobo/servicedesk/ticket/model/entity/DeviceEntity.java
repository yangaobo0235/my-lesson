package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户可选资产的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "it_device", autoResultMap = true)
public class DeviceEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 外部逻辑引用 → 客户组织。
     */
    private String customerId;

    /**
     * 本地 FK → it_product_model。
     */
    private String productModelId;

    /**
     * 客户内规范化资产编号。
     */
    private String assetCode;

    /**
     * 序列号，敏感展示按权限裁剪。
     */
    private String serialNumber;

    /**
     * 使用地点，不作权限依据。
     */
    private String location;

    /**
     * 设备默认区域代码，规范大写ASCII，仅供提单预填；预约校验工单区域/地点。
     */
    private String serviceAreaCode;

    /**
     * ACTIVE / RETIRED。
     */
    private String status;

    /**
     * 外部账号，负责该客户的客服。
     */
    private String createdBy;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时显式维护，默认值不自动随 UPDATE 改变。
     */
    private LocalDateTime updatedAt;

    /**
     * 非负，更新比较旧值后加一。
     */
    private Long rowVersion;
}
