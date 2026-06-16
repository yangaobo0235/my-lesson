package com.yangaobo.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** @author 杨奥博 */
@Schema(name = "预支付DTO")
@Data
public class PrePayDTO implements Serializable {

	@Schema(description = "用户ID：购买人的ID")
	@NotNull(message = "用户ID不能为空")
	private Long fkUserId;

	@Schema(description = "课程主键列表")
	@NotNull(message = "课程主键列表不能为空")
	@Size(min = 1, message = "课程主键列表至少包含一个元素")
	private List<Long> courseIds;

	@Schema(description = "订单总金额")
	private BigDecimal totalAmount;

	@Schema(description = "实际支付金额")
	private BigDecimal payAmount;

	@Schema(description = "优惠卷ID：下单时使用的优惠卷")
	private Long fkCouponsId;
}
