package com.yangaobo.servicedesk.knowledge.model.entity;

/**
 * 草稿适用规则。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@com.baomidou.mybatisplus.annotation.TableName(value = "kb_document_applicability", autoResultMap = true)
public class DocumentRuleEntity implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 主键。 */
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    /** 本地FK→kb_document。 */
    private String documentId;

    /** 修订内正序编号。 */
    private Integer ruleNumber;

    /** 外部ticket服务项目ID。 */
    private String serviceItemId;

    /** 外部ticket型号ID；空表示本规则不限制型号。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String productModelId;

    /** 规范化精确匹配，非空白。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String osName;

    /** 精确版本文本，非空时要求os_name。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String osVersion;

    /** 规范化精确软件名称。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String softwareName;

    /** 精确版本文本，非空时要求software_name。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String softwareVersion;

    /** 规则登记时间。 */
    private java.time.LocalDateTime createdAt;
}
