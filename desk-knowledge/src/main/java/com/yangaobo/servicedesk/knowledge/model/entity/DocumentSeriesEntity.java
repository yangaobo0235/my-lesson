package com.yangaobo.servicedesk.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料系列及固定受众。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "kb_document_series", autoResultMap = true)
public class DocumentSeriesEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，也是发布并发锁定对象。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * OFFICIAL_MANUAL / TROUBLESHOOTING_GUIDE / SANITIZED_CASE。
     */
    private String sourceType;

    /**
     * 规范化发布方或内部目录代码。
     */
    private String sourceNamespace;

    /**
     * 发布方范围内稳定文档号，缺少官方编号时由管理员登记受控目录码。
     */
    private String sourceCode;

    /**
     * 原发布方展示名称。
     */
    private String sourceOrganization;

    /**
     * 规范化语言标识，区分不同语言版。
     */
    private String languageCode;

    /**
     * CUSTOMER / SERVICE_STAFF。
     */
    private String audience;

    /**
     * 外部知识管理员 ID。
     */
    private String createdBy;

    /**
     * 系列登记时间。
     */
    private LocalDateTime createdAt;
}
