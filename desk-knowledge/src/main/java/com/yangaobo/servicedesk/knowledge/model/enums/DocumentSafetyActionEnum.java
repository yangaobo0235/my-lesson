package com.yangaobo.servicedesk.knowledge.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 修订安全动作，发布资格与读取权限分别维护。
 *
 * @author yangaobo
 */
public enum DocumentSafetyActionEnum implements BaseEnum<String> {
    /**
     * 内容存在风险，永久停止本修订的指导资格。
     */
    WITHDRAWN("WITHDRAWN", "风险撤回"),
    /**
     * 禁止新召回和历史正文读取。
     */
    ACCESS_REVOKED("ACCESS_REVOKED", "撤销访问"),
    /**
     * 只恢复读取权限，不恢复发布或指导资格。
     */
    ACCESS_RESTORED("ACCESS_RESTORED", "恢复访问");

    /**
     * 不可变事件中的稳定值。
     */
    private final String value;
    /**
     * 动作的中文含义。
     */
    private final String desc;

    /**
     * 将稳定值与显示含义绑定。
     */
    DocumentSafetyActionEnum(String value, String desc) {
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
