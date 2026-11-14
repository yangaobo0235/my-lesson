package com.yangaobo.servicedesk.user.model.dto.account;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 身份管理审计的内部数据合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class AuditEventRowDTO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 审计标识。 */
    private String id;

    /** USER或BOOTSTRAP。 */
    private String actorType;

    /** 操作者，初始化为空。 */
    private String actorUserId;

    /** 账号目标。 */
    private String targetUserId;

    /** 组织目标。 */
    private String targetOrganizationId;

    /** 不可变事件类型。 */
    private String eventType;

    /** 脱敏前后值与原因。 */
    private com.fasterxml.jackson.databind.JsonNode details;

    /** UTC事件时间。 */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private java.time.LocalDateTime createdAt;
}
