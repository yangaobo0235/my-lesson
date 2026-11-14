package com.yangaobo.servicedesk.user.model.vo.account;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 身份管理审计的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class AuditEventVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 审计标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "审计标识。")
    private String id;

    /** USER或BOOTSTRAP。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "USER或BOOTSTRAP。")
    private String actorType;

    /** 操作者，初始化为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "操作者，初始化为空。")
    private String actorUserId;

    /** 账号目标。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "账号目标。")
    private String targetUserId;

    /** 组织目标。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "组织目标。")
    private String targetOrganizationId;

    /** 不可变事件类型。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "不可变事件类型。")
    private String eventType;

    /** 脱敏前后值与原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "脱敏前后值与原因。")
    private IdentityAuditDetailsVO details;

    /** UTC事件时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC事件时间。")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private java.time.LocalDateTime createdAt;
}
