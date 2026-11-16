package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 已确认环境及逐字段来源；未知保持空值。
 *
 * @author yangaobo
 */
@Data
public class SupportContextVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 操作系统名称。
     */
    @Schema(description = "操作系统名称。")
    private String osName;

    /**
     * 操作系统版本。
     */
    @Schema(description = "操作系统版本。")
    private String osVersion;

    /**
     * 软件名称。
     */
    @Schema(description = "软件名称。")
    private String softwareName;

    /**
     * 软件版本。
     */
    @Schema(description = "软件版本。")
    private String softwareVersion;

    /**
     * 连接类型。
     */
    @Schema(description = "连接类型。")
    private String connectionType;

    /**
     * 错误代码。
     */
    @Schema(description = "错误代码。")
    private String errorCode;

    /**
     * 影响范围。
     */
    @Schema(description = "影响范围。")
    private String affectedScope;

    /**
     * 每个环境字段的事实来源。
     */
    @Schema(description = "每个环境字段的事实来源。")
    private SupportFieldSourcesVO fieldSources;
}
