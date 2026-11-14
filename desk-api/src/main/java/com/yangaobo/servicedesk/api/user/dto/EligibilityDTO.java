package com.yangaobo.servicedesk.api.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * EligibilityDTO的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class EligibilityDTO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 目标客户组织当前有效。 */
    private boolean customerActive;

    /** 指定技术人员当前有效并覆盖该客户。 */
    private boolean assigneeAvailable;
}
