package com.yangaobo.servicedesk.ticket.model.form.support;

import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.Pattern;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 服务与资产分页筛选的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SupportQueryForm extends BasePageForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 服务人员查询目标客户，客户只能查询自己。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "服务人员查询目标客户，客户只能查询自己。")
    @Identifier private String customerId;

    /** 可选服务或设备类别。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选服务或设备类别。")
    @Pattern(regexp = "TERMINAL|PRINTING|NETWORK|OFFICE_SOFTWARE|COMPUTER|PRINTER")
    private String category;

    /** 可选状态。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选状态。")
    @Pattern(regexp = "ACTIVE|DISABLED|SUSPENDED|RETIRED")
    private String status;
}
