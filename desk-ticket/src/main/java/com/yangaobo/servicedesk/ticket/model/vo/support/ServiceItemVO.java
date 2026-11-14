package com.yangaobo.servicedesk.ticket.model.vo.support;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yangaobo.servicedesk.common.serialize.DecimalStringSerializer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * IT服务项目的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class ServiceItemVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键。")
    private String id;

    /** 规范化大写ASCII稳定代码，全局唯一且停用不复用。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "规范化大写ASCII稳定代码，全局唯一且停用不复用。")
    private String serviceCode;

    /** TERMINAL / PRINTING / NETWORK / OFFICE_SOFTWARE。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "TERMINAL / PRINTING / NETWORK / OFFICE_SOFTWARE。")
    private String category;

    /** 客户可理解的项目名称。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "客户可理解的项目名称。")
    private String serviceName;

    /** 明确支持产品、环境、版本及排除条件，最多5000字符。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "明确支持产品、环境、版本及排除条件，最多5000字符。")
    private String supportScope;

    /** 项目是否允许人工开放现场服务。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "项目是否允许人工开放现场服务。")
    private Boolean onsiteSupported;

    /** ACTIVE / DISABLED。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "ACTIVE / DISABLED。")
    private String status;

    /** 本次维护原因。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本次维护原因。")
    private String changeReason;

    /** 记录创建时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "记录创建时间。")
    private java.time.Instant createdAt;

    /** 显式维护更新时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "显式维护更新时间。")
    private java.time.Instant updatedAt;

    /** 非负，比较后递增。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "非负，比较后递增。")
    @JsonSerialize(using = DecimalStringSerializer.class)
    private Long rowVersion;
}
