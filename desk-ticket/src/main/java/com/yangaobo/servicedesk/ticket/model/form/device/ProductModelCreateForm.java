package com.yangaobo.servicedesk.ticket.model.form.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 产品型号登记的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ProductModelCreateForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 电脑或打印机。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "电脑或打印机。")
    @NotBlank
    @Pattern(regexp = "COMPUTER|PRINTER")
    private String category;

    /** 品牌。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "品牌。")
    @NotBlank
    @Size(max = 100)
    private String brand;

    /** 准确型号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "准确型号。")
    @NotBlank
    @Size(max = 100)
    private String modelName;

    /** 已确认系统适用说明。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "已确认系统适用说明。")
    @Size(max = 500)
    private String supportedSystems;
}
