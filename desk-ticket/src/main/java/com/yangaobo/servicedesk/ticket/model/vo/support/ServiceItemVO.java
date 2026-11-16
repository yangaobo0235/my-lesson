package com.yangaobo.servicedesk.ticket.model.vo.support;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IT服务项目的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ServiceItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @Schema(description = "主键。")
    private String id;

    /**
     * 规范化大写ASCII稳定代码，全局唯一且停用不复用。
     */
    @Schema(description = "规范化大写ASCII稳定代码，全局唯一且停用不复用。")
    private String serviceCode;

    /**
     * TERMINAL / PRINTING / NETWORK / OFFICE_SOFTWARE。
     */
    @Schema(description = "TERMINAL / PRINTING / NETWORK / OFFICE_SOFTWARE。")
    private String category;

    /**
     * 客户可理解的项目名称。
     */
    @Schema(description = "客户可理解的项目名称。")
    private String serviceName;

    /**
     * 明确支持产品、环境、版本及排除条件，最多5000字符。
     */
    @Schema(description = "明确支持产品、环境、版本及排除条件，最多5000字符。")
    private String supportScope;

    /**
     * 项目是否允许人工开放现场服务。
     */
    @Schema(description = "项目是否允许人工开放现场服务。")
    private Boolean onsiteSupported;

    /**
     * ACTIVE / DISABLED。
     */
    @Schema(description = "ACTIVE / DISABLED。")
    private String status;

    /**
     * 本次维护原因。
     */
    @Schema(description = "本次维护原因。")
    private String changeReason;

    /**
     * 记录创建时间。
     */
    @Schema(description = "记录创建时间。")
    private Instant createdAt;

    /**
     * 显式维护更新时间。
     */
    @Schema(description = "显式维护更新时间。")
    private Instant updatedAt;

    /**
     * 非负，比较后递增。
     */
    @Schema(
            description = "非负，比较后递增。",
            type = "string",
            pattern = "^(0|[1-9][0-9]*)$",
            example = "1")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
