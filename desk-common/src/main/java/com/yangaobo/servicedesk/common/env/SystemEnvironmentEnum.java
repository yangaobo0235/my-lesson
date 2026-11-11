package com.yangaobo.servicedesk.common.env;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 定义应用基础环境，不携带连接或凭据。
 *
 * @author yangaobo
 */
public enum SystemEnvironmentEnum implements BaseEnum<String> {

    /**
     * 开发环境，对应隔离的test资源。
     */
    DEV("dev", "开发环境"),

    /**
     * 生产环境，对应prod资源。
     */
    PROD("prod", "生产环境");

    /**
     * 配置中的基础环境名称。
     */
    private final String value;

    /**
     * 环境中文说明。
     */
    private final String desc;

    /**
     * 固定环境值与说明。
     *
     * @param value 配置线值
     * @param desc 中文说明
     */
    SystemEnvironmentEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /** {@inheritDoc} */
    @Override
    public String getValue() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public String getDesc() {
        return desc;
    }
}
