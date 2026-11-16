package com.yangaobo.servicedesk.ticket.model.form.support;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 服务与资产分页筛选的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SupportQueryForm extends BasePageForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务人员查询目标客户，客户只能查询自己。
     */
    @Schema(description = "服务人员查询目标客户，客户只能查询自己。")
    @Identifier
    private String customerId;

    /**
     * 可选服务或设备类别。
     */
    @Schema(description = "可选服务或设备类别。")
    @Pattern(regexp = "TERMINAL|PRINTING|NETWORK|OFFICE_SOFTWARE|COMPUTER|PRINTER")
    private String category;

    /**
     * 可选状态。
     */
    @Schema(description = "可选状态。")
    @Pattern(regexp = "ACTIVE|DISABLED|SUSPENDED|RETIRED")
    private String status;
}
