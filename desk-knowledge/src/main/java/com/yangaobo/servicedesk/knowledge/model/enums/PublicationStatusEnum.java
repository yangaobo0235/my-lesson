package com.yangaobo.servicedesk.knowledge.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 修订发布及当前系列位置，保持数据库和接口的稳定值。
 *
 * @author yangaobo
 */
public enum PublicationStatusEnum implements BaseEnum<String> {
    /**
     * 草稿。
     */
    DRAFT("DRAFT", "草稿"),
    /**
     * 已发布。
     */
    PUBLISHED("PUBLISHED", "已发布"),
    /**
     * 已停用。
     */
    DISABLED("DISABLED", "已停用"),
    /**
     * 已退役。
     */
    RETIRED("RETIRED", "已退役"),
    /**
     * 未发布草稿已删除。
     */
    DELETED("DELETED", "未发布草稿已删除");

    /**
     * 稳定线值。
     */
    private final String value;

    /**
     * 中文含义。
     */
    private final String desc;

    /**
     * 将线值与业务含义绑定，枚举不改变全局JSON行为。
     */
    PublicationStatusEnum(String value, String desc) {
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
