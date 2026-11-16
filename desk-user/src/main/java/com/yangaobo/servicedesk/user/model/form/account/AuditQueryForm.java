package com.yangaobo.servicedesk.user.model.form.account;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 管理审计筛选的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AuditQueryForm extends BasePageForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 账号目标，与组织目标互斥。
     */
    @Schema(description = "账号目标，与组织目标互斥。")
    @Identifier
    private String targetUserId;

    /**
     * 组织目标，与账号目标互斥。
     */
    @Schema(description = "组织目标，与账号目标互斥。")
    @Identifier
    private String targetOrganizationId;
}
