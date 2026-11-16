package com.yangaobo.servicedesk.ticket.model.form.support;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 来源化固定环境的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class SupportContextForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户反馈的系统名称。
     */
    @Schema(description = "用户反馈的系统名称。")
    @Size(max = 100)
    private String osName;

    /**
     * 系统版本，有版本须有名称。
     */
    @Schema(description = "系统版本，有版本须有名称。")
    @Size(max = 100)
    private String osVersion;

    /**
     * 软件名称。
     */
    @Schema(description = "软件名称。")
    @Size(max = 100)
    private String softwareName;

    /**
     * 软件版本，有版本须有名称。
     */
    @Schema(description = "软件版本，有版本须有名称。")
    @Size(max = 100)
    private String softwareVersion;

    /**
     * 错误码，未知为空。
     */
    @Schema(description = "错误码，未知为空。")
    @Size(max = 100)
    private String errorCode;

    /**
     * 连接类型，未知明确标记。
     */
    @Schema(description = "连接类型，未知明确标记。")
    @NotNull
    @Pattern(regexp = "USB|LAN|WIFI|VPN|OTHER|UNKNOWN")
    private String connectionType = "UNKNOWN";

    /**
     * 影响范围。
     */
    @Schema(description = "影响范围。")
    @NotNull
    @Pattern(regexp = "SINGLE_USER|MULTIPLE_USERS|UNKNOWN")
    private String affectedScope = "UNKNOWN";
}
