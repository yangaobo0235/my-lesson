package com.yangaobo.servicedesk.user.model.vo.account;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 审计中的安全历史字段，未知字段不输出，不含任何口令。
 *
 * @author yangaobo
 */
@Data
public class IdentityAuditStateVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更时的id，不适用时为空。
     */
    @Schema(description = "变更时的id，不适用时为空。")
    private String id;

    /**
     * 变更时的organizationId，不适用时为空。
     */
    @Schema(description = "变更时的organizationId，不适用时为空。")
    private String organizationId;

    /**
     * 变更时的organizationType，不适用时为空。
     */
    @Schema(description = "变更时的organizationType，不适用时为空。")
    private String organizationType;

    /**
     * 变更时的organizationCode，不适用时为空。
     */
    @Schema(description = "变更时的organizationCode，不适用时为空。")
    private String organizationCode;

    /**
     * 变更时的organizationName，不适用时为空。
     */
    @Schema(description = "变更时的organizationName，不适用时为空。")
    private String organizationName;

    /**
     * 变更时的username，不适用时为空。
     */
    @Schema(description = "变更时的username，不适用时为空。")
    private String username;

    /**
     * 变更时的displayName，不适用时为空。
     */
    @Schema(description = "变更时的displayName，不适用时为空。")
    private String displayName;

    /**
     * 变更时的status，不适用时为空。
     */
    @Schema(description = "变更时的status，不适用时为空。")
    private String status;

    /**
     * 变更时的rowVersion，不适用时为空。
     */
    @Schema(description = "变更时的rowVersion，不适用时为空。")
    private String rowVersion;

    /**
     * 变更时的createdAt，不适用时为空。
     */
    @Schema(description = "变更时的createdAt，不适用时为空。")
    private String createdAt;

    /**
     * 变更时的updatedAt，不适用时为空。
     */
    @Schema(description = "变更时的updatedAt，不适用时为空。")
    private String updatedAt;

    /**
     * 变更时的mustChangePassword，不适用时为空。
     */
    @Schema(description = "变更时的mustChangePassword，不适用时为空。")
    private Boolean mustChangePassword;

    /**
     * 变更时的roles，不适用时为空。
     */
    @Schema(description = "变更时的roles，不适用时为空。")
    private List<String> roles;

    /**
     * 变更时的customerIds，不适用时为空。
     */
    @Schema(description = "变更时的customerIds，不适用时为空。")
    private List<String> customerIds;

    /**
     * 初始化时记录的组织名称或账号名。
     */
    @Schema(description = "初始化时记录的组织名称或账号名。")
    private String bootstrapName;
}
