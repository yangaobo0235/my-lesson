package com.yangaobo.servicedesk.user.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 账号角色，明确保持数据库与接口的稳定线值。
 *
 * @author yangaobo
 */
public enum RoleEnum implements BaseEnum<String> {
    /**
     * 客户用户。
     */
    CUSTOMER_USER("CUSTOMER_USER", "客户用户"),
    /**
     * 客服。
     */
    SERVICE_AGENT("SERVICE_AGENT", "客服"),
    /**
     * 技术人员。
     */
    TECHNICIAN("TECHNICIAN", "技术人员"),
    /**
     * 知识管理员。
     */
    KNOWLEDGE_ADMIN("KNOWLEDGE_ADMIN", "知识管理员"),
    /**
     * 系统管理员。
     */
    SYSTEM_ADMIN("SYSTEM_ADMIN", "系统管理员");

    /**
     * 稳定线值。
     */
    private final String value;

    /**
     * 中文含义。
     */
    private final String desc;

    /**
     * 关联线值和中文含义。
     */
    RoleEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 返回稳定线值。
     */
    @Override
    public String getValue() {
        return value;
    }

    /**
     * 返回中文含义。
     */
    @Override
    public String getDesc() {
        return desc;
    }
}
