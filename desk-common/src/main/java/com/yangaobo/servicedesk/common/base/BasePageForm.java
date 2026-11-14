package com.yangaobo.servicedesk.common.base;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * BasePageForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class BasePageForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 从1开始的页号。 */
    @NotNull
    @Min(1)
    @Max(100000)
    private Integer pageNum = 1;

    /** 每页条数，最大100。 */
    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;
}
