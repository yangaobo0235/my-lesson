package com.yangaobo.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;

/** @author 杨奥博 */
@Schema(name = "查询预支付二维码DTO")
@Data
public class QrCodeDTO implements Serializable {

	@Schema(description = "订单编号")
	@NotEmpty(message = "订单编号不能为空")
	private String sn;

}
