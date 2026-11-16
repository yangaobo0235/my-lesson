package com.yangaobo.servicedesk.common.base;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BasePageForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class BasePageForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 从1开始的页号。
     */
    @NotNull
    @Min(1)
    private Integer pageNum = 1;

    /**
     * 每页条数，最大100。
     */
    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    /**
     * 返回数据库分页偏移；内部调用同样拒绝非法分页，不静默截断。
     */
    public long offset() {
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new com.yangaobo.servicedesk.common.exception.BusinessException(
                    CommonErrorCode.INVALID_REQUEST);
        }
        return ((long) pageNum - 1) * pageSize;
    }
}
