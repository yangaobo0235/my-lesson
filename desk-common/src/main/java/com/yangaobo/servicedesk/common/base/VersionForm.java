package com.yangaobo.servicedesk.common.base;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VersionForm的业务合同。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class VersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户端读取的当前版本。
     */
    @NotNull
    @Min(0)
    private Long rowVersion;
}
