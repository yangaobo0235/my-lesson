package com.yangaobo.servicedesk.knowledge.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 独立于发布状态的资料读取权限。
 *
 * @author yangaobo
 */
public enum DocumentAccessStatusEnum implements BaseEnum<String> {
    /**
     * 仍需核对当前身份、受众和适用范围。
     */
    ALLOWED("ALLOWED", "允许授权读取"),
    /**
     * 包括历史来源在内均停止读取。
     */
    REVOKED("REVOKED", "已撤销访问");

    /**
     * 稳定持久值。
     */
    private final String value;
    /**
     * 中文含义。
     */
    private final String desc;

    /**
     * 绑定持久值和中文含义。
     */
    DocumentAccessStatusEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getValue() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDesc() {
        return desc;
    }
}
