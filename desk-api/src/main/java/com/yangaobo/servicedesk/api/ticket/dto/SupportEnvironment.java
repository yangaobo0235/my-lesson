package com.yangaobo.servicedesk.api.ticket.dto;

/**
 * 具名问题环境及未知条件。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class SupportEnvironment implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 系统名称。 */
    private String osName;

    /** 系统版本。 */
    private String osVersion;

    /** 软件名称。 */
    private String softwareName;

    /** 软件版本。 */
    private String softwareVersion;

    /** 连接类型，UNKNOWN表示未知。 */
    private String connectionType;

    /** 影响范围，UNKNOWN表示未知。 */
    private String affectedScope;

    /** 完整字面错误码。 */
    private String errorCode;
}
