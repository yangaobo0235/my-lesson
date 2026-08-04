package com.yangaobo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/** @author 杨奥博 */
@Schema(name = "秒杀DTO")
@Data
public class KillDTO implements Serializable {

	@Schema(description = "客户端生成的稳定业务请求 ID")
	@NotNull(message = "requestId 不能为空")
	private UUID requestId;

	@Schema(description = "活动ID")
	@NotNull(message = "活动ID不能为空")
	private Long fkSeckillId;

	@Schema(description = "用户ID", accessMode = Schema.AccessMode.READ_ONLY)
	private Long fkUserId;

	@Schema(description = "课程ID")
	@NotNull(message = "课程ID不能为空")
	private Long fkCourseId;

	@Schema(description = "课程秒杀价格", accessMode = Schema.AccessMode.READ_ONLY)
	private Double skPrice;

	@Schema(description = "课程原价", accessMode = Schema.AccessMode.READ_ONLY)
	private Double price;
}
