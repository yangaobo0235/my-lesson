package com.yangaobo.servicedesk.knowledge.model.entity;

/**
 * 资料系列及固定受众。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@com.baomidou.mybatisplus.annotation.TableName(value = "kb_document_series", autoResultMap = true)
public class DocumentSeriesEntity implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键，也是发布并发锁定对象。 */
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    /** OFFICIAL_MANUAL / TROUBLESHOOTING_GUIDE / SANITIZED_CASE。 */
    private String sourceType;

    /** 规范化发布方或内部目录代码。 */
    private String sourceNamespace;

    /** 发布方范围内稳定文档号，缺少官方编号时由管理员登记受控目录码。 */
    private String sourceCode;

    /** 原发布方展示名称。 */
    private String sourceOrganization;

    /** 规范化语言标识，区分不同语言版。 */
    private String languageCode;

    /** CUSTOMER / SERVICE_STAFF。 */
    private String audience;

    /** 外部知识管理员 ID。 */
    private String createdBy;

    /** 系列登记时间。 */
    private java.time.LocalDateTime createdAt;
}
