package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 工单记录可见性，明确保持数据库与接口的稳定线值。
 *
 * @author yangaobo
 */
public enum VisibilityEnum implements BaseEnum<String> {
    /**
     * 客户及服务团队。
     */
    PUBLIC("PUBLIC", "客户及服务团队"),
    /**
     * 仅服务团队。
     */
    INTERNAL("INTERNAL", "仅服务团队");

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
    VisibilityEnum(String value, String desc) {
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
