package com.yangaobo.servicedesk.common.base;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PageVO<T>的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class PageVO<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页授权结果。
     */
    private java.util.List<T> records;

    /**
     * 同一授权条件下的总数。
     */
    @io.swagger.v3.oas.annotations.media.Schema(
            type = "string",
            pattern = "^(0|[1-9][0-9]*)$",
            example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long total;

    /**
     * 当前页号。
     */
    private Integer pageNum;

    /**
     * 每页条数。
     */
    private Integer pageSize;

    /**
     * 统一构造完整分页结果，调用者仍负责相同授权与筛选条件下的计数。
     */
    public static <T> PageVO<T> of(java.util.List<T> records, long total, BasePageForm form) {
        form.offset();
        if (total < 0) throw new IllegalArgumentException("分页总数不能为负数");
        PageVO<T> result = new PageVO<>();
        result.setRecords(java.util.List.copyOf(records));
        result.setTotal(total);
        result.setPageNum(form.getPageNum());
        result.setPageSize(form.getPageSize());
        return result;
    }
}
