package com.yangaobo.servicedesk.common.base;

import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * IdVersionForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class IdVersionForm extends VersionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 目标业务标识。 */
    @NotBlank @Identifier private String id;
}
