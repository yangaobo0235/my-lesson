package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 结单原因，明确保持数据库与接口的稳定线值。
 *
 * @author yangaobo
 */
public enum ClosureReasonEnum implements BaseEnum<String> {
    /** 客户确认。 */
    CUSTOMER_CONFIRMED("CUSTOMER_CONFIRMED", "客户确认"),
    /** 超过72小时未反馈。 */
    CONFIRMATION_TIMEOUT("CONFIRMATION_TIMEOUT", "超过72小时未反馈");

    /** 稳定线值。 */
    private final String value;

    /** 中文含义。 */
    private final String desc;

    /** 关联线值和中文含义。 */
    ClosureReasonEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /** 返回稳定线值。 */
    @Override
    public String getValue() {
        return value;
    }

    /** 返回中文含义。 */
    @Override
    public String getDesc() {
        return desc;
    }
}
