package com.yangaobo.servicedesk.user.model.vo.account;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账号的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class AccountVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 账号标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "账号标识。")
    private String id;

    /** 归属组织。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "归属组织。")
    private String organizationId;

    /** 账号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "账号。")
    private String username;

    /** 姓名。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "姓名。")
    private String displayName;

    /** 状态。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "状态。")
    private String status;

    /** 是否须改密。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "是否须改密。")
    private Boolean mustChangePassword;

    /** 当前版本。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前版本。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;

    /** 完整角色集合。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "完整角色集合。")
    private java.util.Set<String> roles;

    /** 完整人员客户范围。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "完整人员客户范围。")
    private java.util.Set<String> customerIds;
}
