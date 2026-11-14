package com.yangaobo.servicedesk.common.base;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * PageVO<T>的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class PageVO<T> implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 当前页授权结果。 */
    private java.util.List<T> records;

    /** 同一授权条件下的总数。 */
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long total;

    /** 当前页号。 */
    private Integer pageNum;

    /** 每页条数。 */
    private Integer pageSize;
}
