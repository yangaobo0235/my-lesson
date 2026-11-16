package com.yangaobo.servicedesk.ticket.model.form.device;

import com.yangaobo.servicedesk.common.base.IdVersionForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 设备资料调整的输入约束。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DeviceUpdateForm extends IdVersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户内资产编号，统一大写。
     */
    @Schema(description = "客户内资产编号，统一大写。")
    @NotBlank
    @Size(max = 64)
    private String assetCode;

    /**
     * 可选序列号，可清空。
     */
    @Schema(description = "可选序列号，可清空。")
    @Size(max = 100)
    private String serialNumber;

    /**
     * 设备地点，可清空。
     */
    @Schema(description = "设备地点，可清空。")
    @Size(max = 200)
    private String location;

    /**
     * 可选现场区域代码。
     */
    @Schema(description = "可选现场区域代码。")
    @Pattern(regexp = "[A-Z0-9_-]{1,32}")
    private String serviceAreaCode;
}
