package com.yangaobo.servicedesk.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 映射身份管理的不可变审计事实，仅由完整管理用例追加。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "sys_admin_event", autoResultMap = true)
public class AdminEventEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计标识。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * USER或BOOTSTRAP。
     */
    private String actorType;

    /**
     * 操作者，初始化为空。
     */
    private String actorUserId;

    /**
     * 账号目标。
     */
    private String targetUserId;

    /**
     * 组织目标。
     */
    private String targetOrganizationId;

    /**
     * 不可变事件类型。
     */
    private String eventType;

    /**
     * 脱敏前后值与原因。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode details;

    /**
     * UTC事件时间。
     */
    private LocalDateTime createdAt;
}
