package com.yangaobo.servicedesk.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 账号的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName("sys_user")
public class AccountEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 本库 FK → sys_organization。
     */
    private String organizationId;

    /**
     * 去首尾空格、转小写，全局唯一。
     */
    private String username;

    /**
     * Spring Security 哈希及算法标识，不能输出。
     */
    @ToString.Exclude
    private String passwordHash;

    /**
     * 姓名，不作授权条件。
     */
    private String displayName;

    /**
     * ACTIVE / DISABLED。
     */
    private String status;

    /**
     * 初始化和重置后强制改密。
     */
    private Boolean mustChangePassword;

    /**
     * 首次主动改密前为空。
     */
    private LocalDateTime passwordChangedAt;

    /**
     * 仅认证成功更新。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 改密、角色、组织、客户范围或停用等认证相关变更加一。
     */
    private Long authVersion;

    /**
     * 本库 FK → sys_user，首次管理员初始化可空。
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
