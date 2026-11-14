package com.yangaobo.servicedesk.ticket.model.form.device;

import com.yangaobo.servicedesk.common.validator.Identifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户设备登记的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class DeviceCreateForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 所属客户，登记后固定。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "所属客户，登记后固定。")
    @NotBlank @Identifier private String customerId;

    /** 产品型号，登记后固定。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "产品型号，登记后固定。")
    @NotBlank @Identifier private String productModelId;

    /** 客户内资产编号，统一大写。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "客户内资产编号，统一大写。")
    @NotBlank
    @Size(max = 64)
    private String assetCode;

    /** 可选序列号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选序列号。")
    @Size(max = 100)
    private String serialNumber;

    /** 可选设备地点，仅用于预填。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选设备地点，仅用于预填。")
    @Size(max = 200)
    private String location;

    /** 可选现场区域代码。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "可选现场区域代码。")
    @Pattern(regexp = "[A-Z0-9_-]{1,32}")
    private String serviceAreaCode;
}
