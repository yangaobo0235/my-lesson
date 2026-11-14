package com.yangaobo.servicedesk.api.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * IdentityDTO的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class IdentityDTO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 当前有效用户标识。 */
    private String ownerId;

    /** 当前有效组织。 */
    private String organizationId;

    /** CUSTOMER或SERVICE_PROVIDER。 */
    private String organizationType;

    /** 显示姓名。 */
    private String displayName;

    /** 当前角色，不能由浏览器声明。 */
    private java.util.Set<String> roles;

    /** 当前客服或技术人员客户范围。 */
    private java.util.Set<String> customerIds;

    /** 身份版本。 */
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long authVersion;

    /** 不可登录的会话主键引用。 */
    @lombok.ToString.Exclude private String sessionRef;

    /** 首次或重置后必须改密。 */
    private Boolean mustChangePassword;
}
