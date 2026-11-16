package com.yangaobo.servicedesk.ticket.model.form.device;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 产品型号资料调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductModelUpdateForm extends IdVersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 品牌。
     */
    @Schema(description = "品牌。")
    @NotBlank
    @Size(max = 100)
    private String brand;

    /**
     * 准确型号。
     */
    @Schema(description = "准确型号。")
    @NotBlank
    @Size(max = 100)
    private String modelName;

    /**
     * 已确认系统适用说明，可清空。
     */
    @Schema(description = "已确认系统适用说明，可清空。")
    @Size(max = 500)
    private String supportedSystems;
}
