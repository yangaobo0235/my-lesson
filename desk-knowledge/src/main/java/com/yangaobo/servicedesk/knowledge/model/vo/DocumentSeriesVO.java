package com.yangaobo.servicedesk.knowledge.model.vo;

/**
 * 资料系列及固定受众的公开投影。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class DocumentSeriesVO implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键，也是发布并发锁定对象。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "主键，也是发布并发锁定对象")
    private String id;

    /** OFFICIAL_MANUAL / TROUBLESHOOTING_GUIDE / SANITIZED_CASE。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "OFFICIAL_MANUAL / TROUBLESHOOTING_GUIDE / SANITIZED_CASE")
    private String sourceType;

    /** 规范化发布方或内部目录代码。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "规范化发布方或内部目录代码")
    private String sourceNamespace;

    /** 发布方范围内稳定文档号，缺少官方编号时由管理员登记受控目录码。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "发布方范围内稳定文档号，缺少官方编号时由管理员登记受控目录码")
    private String sourceCode;

    /** 原发布方展示名称。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "原发布方展示名称")
    private String sourceOrganization;

    /** 规范化语言标识，区分不同语言版。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "规范化语言标识，区分不同语言版")
    private String languageCode;

    /** CUSTOMER / SERVICE_STAFF。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "CUSTOMER / SERVICE_STAFF")
    private String audience;

    /** 外部知识管理员 ID。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "外部知识管理员 ID")
    private String createdBy;

    /** 系列登记时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "系列登记时间")
    private java.time.Instant createdAt;
}
