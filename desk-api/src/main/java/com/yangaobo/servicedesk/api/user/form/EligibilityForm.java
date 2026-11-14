package com.yangaobo.servicedesk.api.user.form;

import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * EligibilityForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EligibilityForm extends VerifySessionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 待核验客户组织。 */
    @NotBlank @Identifier private String customerId;

    /** 待核验技术人员，客户核验时为空。 */
    @Identifier private String assigneeId;
}
