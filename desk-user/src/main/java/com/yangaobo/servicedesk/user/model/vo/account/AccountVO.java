package com.yangaobo.servicedesk.user.model.vo.account;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class AccountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 账号标识。
     */
    @Schema(description = "账号标识。")
    private String id;

    /**
     * 归属组织。
     */
    @Schema(description = "归属组织。")
    private String organizationId;

    /**
     * 账号。
     */
    @Schema(description = "账号。")
    private String username;

    /**
     * 姓名。
     */
    @Schema(description = "姓名。")
    private String displayName;

    /**
     * 状态。
     */
    @Schema(description = "状态。")
    private String status;

    /**
     * 是否须改密。
     */
    @Schema(description = "是否须改密。")
    private Boolean mustChangePassword;

    /**
     * 当前版本。
     */
    @Schema(description = "当前版本。", type = "string", pattern = "^(0|[1-9][0-9]*)$", example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;

    /**
     * 完整角色集合。
     */
    @Schema(description = "完整角色集合。")
    private Set<String> roles;

    /**
     * 完整人员客户范围。
     */
    @Schema(description = "完整人员客户范围。")
    private Set<String> customerIds;
}
