package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 工单状态，明确保持数据库与接口的稳定线值。
 *
 * @author yangaobo
 */
public enum TicketStatusEnum implements BaseEnum<String> {
    /** 待受理。 */
    SUBMITTED("SUBMITTED", "待受理"),
    /** 处理中。 */
    PROCESSING("PROCESSING", "处理中"),
    /** 待补充。 */
    WAITING_CUSTOMER("WAITING_CUSTOMER", "待补充"),
    /** 待客户确认。 */
    RESOLVED("RESOLVED", "待客户确认"),
    /** 已结单。 */
    CLOSED("CLOSED", "已结单"),
    /** 已取消。 */
    CANCELLED("CANCELLED", "已取消");

    /** 稳定线值。 */
    private final String value;

    /** 中文含义。 */
    private final String desc;

    /** 关联线值和中文含义。 */
    TicketStatusEnum(String value, String desc) {
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
