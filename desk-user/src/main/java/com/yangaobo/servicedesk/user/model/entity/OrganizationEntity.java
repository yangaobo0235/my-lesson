package com.yangaobo.servicedesk.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName("sys_organization")
public class OrganizationEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 规范化小写后全局唯一，停用不复用。
     */
    private String organizationCode;

    /**
     * 展示名称，不作为授权标识。
     */
    private String organizationName;

    /**
     * CUSTOMER / SERVICE_PROVIDER。
     */
    private String organizationType;

    /**
     * ACTIVE / DISABLED。
     */
    private String status;

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
