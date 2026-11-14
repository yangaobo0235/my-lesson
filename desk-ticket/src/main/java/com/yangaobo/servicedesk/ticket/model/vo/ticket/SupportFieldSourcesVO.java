package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开环境来源标签，不允许用户自行提交可信来源。
 *
 * @author yangaobo
 */
@Data
public class SupportFieldSourcesVO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 对应环境字段的来源，未知为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应环境字段的来源，未知为空。")
    private String osName;

    /** 对应环境字段的来源，未知为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应环境字段的来源，未知为空。")
    private String osVersion;

    /** 对应环境字段的来源，未知为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应环境字段的来源，未知为空。")
    private String softwareName;

    /** 对应环境字段的来源，未知为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应环境字段的来源，未知为空。")
    private String softwareVersion;

    /** 对应环境字段的来源，未知为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应环境字段的来源，未知为空。")
    private String connectionType;

    /** 对应环境字段的来源，未知为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应环境字段的来源，未知为空。")
    private String errorCode;

    /** 对应环境字段的来源，未知为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应环境字段的来源，未知为空。")
    private String affectedScope;
}
