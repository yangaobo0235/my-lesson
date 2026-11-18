package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 附件稳定线值。
 *
 * @author yangaobo
 */
public enum AttachmentStatusEnum implements BaseEnum<String> {
    /**
     * 意图已登记。
     */
    UPLOADING("UPLOADING", "意图已登记"),
    /**
     * 原件已核验。
     */
    READY("READY", "原件已核验"),
    /**
     * 确定失败。
     */
    FAILED("FAILED", "确定失败"),
    /**
     * 外部结果未知。
     */
    UNKNOWN("UNKNOWN", "外部结果未知"),
    /**
     * 逻辑撤销。
     */
    DELETED("DELETED", "逻辑撤销");
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
    AttachmentStatusEnum(String value, String desc) {
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
