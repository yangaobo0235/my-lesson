package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * IT服务项目的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "it_service_item", autoResultMap = true)
public class ServiceItemEntity implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 规范化大写ASCII稳定代码，全局唯一且停用不复用。 */
    private String serviceCode;

    /** TERMINAL / PRINTING / NETWORK / OFFICE_SOFTWARE。 */
    private String category;

    /** 客户可理解的项目名称。 */
    private String serviceName;

    /** 明确支持产品、环境、版本及排除条件，最多5000字符。 */
    private String supportScope;

    /** 项目是否允许人工开放现场服务。 */
    private Boolean onsiteSupported;

    /** ACTIVE / DISABLED。 */
    private String status;

    /** 外部系统管理员账号。 */
    private String createdBy;

    /** 最后维护管理员账号。 */
    private String updatedBy;

    /** 本次维护原因。 */
    private String changeReason;

    /** 记录创建时间。 */
    private java.time.LocalDateTime createdAt;

    /** 显式维护更新时间。 */
    private java.time.LocalDateTime updatedAt;

    /** 非负，比较后递增。 */
    private Long rowVersion;
}
