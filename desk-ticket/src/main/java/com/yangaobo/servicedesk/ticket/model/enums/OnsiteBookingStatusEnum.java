package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 预约确定结果及履约状态，保持数据库和接口的稳定值。
 *
 * @author yangaobo
 */
public enum OnsiteBookingStatusEnum implements BaseEnum<String> {
    /**
     * 已确认。
     */
    CONFIRMED("CONFIRMED", "已确认"),
    /**
     * 已拒绝。
     */
    REJECTED("REJECTED", "已拒绝"),
    /**
     * 已取消。
     */
    CANCELLED("CANCELLED", "已取消"),
    /**
     * 已履约登记。
     */
    FINISHED("FINISHED", "已履约登记");

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
    OnsiteBookingStatusEnum(String value, String desc) {
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
