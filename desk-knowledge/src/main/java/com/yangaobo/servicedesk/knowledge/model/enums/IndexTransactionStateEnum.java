package com.yangaobo.servicedesk.knowledge.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 提交及取消准备的持久事务事实，保持数据库和接口的稳定值。
 *
 * @author yangaobo
 */
public enum IndexTransactionStateEnum implements BaseEnum<String> {
    /**
     * 尚未请求取消。
     */
    NONE("NONE", "尚未请求取消"),
    /**
     * 准备未决。
     */
    PREPARING("PREPARING", "准备未决"),
    /**
     * 本地已提交。
     */
    COMMITTED("COMMITTED", "本地已提交"),
    /**
     * 本次准备已终止。
     */
    ABORTED("ABORTED", "本次准备已终止");

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
    IndexTransactionStateEnum(String value, String desc) {
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
