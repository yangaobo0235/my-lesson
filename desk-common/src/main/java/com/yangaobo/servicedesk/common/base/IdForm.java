package com.yangaobo.servicedesk.common.base;

import com.yangaobo.servicedesk.common.validator.Identifier;
import jakarta.validation.constraints.NotBlank;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IdForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class IdForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标业务标识。
     */
    @NotBlank
    @Identifier
    private String id;
}
