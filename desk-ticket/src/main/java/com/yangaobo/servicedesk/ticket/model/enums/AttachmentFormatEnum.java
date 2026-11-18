package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 附件稳定线值。
 *
 * @author yangaobo
 */
public enum AttachmentFormatEnum implements BaseEnum<String> {
    /**
     * 纯文本。
     */
    TEXT("TEXT", "纯文本"),
    /**
     * 日志文本。
     */
    LOG("LOG", "日志文本"),
    /**
     * 结构化文本。
     */
    JSON("JSON", "结构化文本");
    /**
     * 稳定线值。
     */
    private final String value;
    /**
     * 中文说明。
     */
    private final String desc;

    /**
     * 关联线值与说明。
     */
    AttachmentFormatEnum(String value, String desc) {
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
