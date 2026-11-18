package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 用户动作与时间线事件的明确映射；系统期限事件不属于用户动作。
 *
 * @author yangaobo
 */
public enum TicketActionTypeEnum implements BaseEnum<String> {
    /**
     * 建单。
     */
    CREATE_TICKET("CREATED", "建单"),
    /**
     * 接单。
     */
    ACCEPT("ACCEPTED", "接单"),
    /**
     * 指派。
     */
    ASSIGN("ASSIGNED", "指派"),
    /**
     * 转派。
     */
    REASSIGN("REASSIGNED", "转派"),
    /**
     * 调整优先级。
     */
    PRIORITY("PRIORITY_CHANGED", "调整优先级"),
    /**
     * 修改环境。
     */
    CONTEXT_UPDATE("CONTEXT_UPDATED", "修改环境"),
    /**
     * 处理记录。
     */
    ADD_RECORD("COMMENTED", "处理记录"),
    /**
     * 请求补充。
     */
    REQUEST_INFORMATION("NEED_INFO", "请求补充"),
    /**
     * 补充反馈。
     */
    SUPPLEMENT("SUPPLEMENTED", "补充反馈"),
    /**
     * 等待外部。
     */
    WAIT_EXTERNAL("WAIT_EXTERNAL", "等待外部"),
    /**
     * 结束等待。
     */
    END_WAIT("END_WAIT", "结束等待"),
    /**
     * 续期跟进。
     */
    FOLLOW_UP("FOLLOW_UP", "续期跟进"),
    /**
     * 解决工单。
     */
    RESOLVE_TICKET("RESOLVED", "解决工单"),
    /**
     * 确认结单。
     */
    CONFIRM("CLOSED", "确认结单"),
    /**
     * 反馈未解决。
     */
    UNRESOLVED("UNRESOLVED", "反馈未解决"),
    /**
     * 取消工单。
     */
    CANCEL("CANCELLED", "取消工单"),
    /**
     * 核对现场资格。
     */
    ONSITE_ELIGIBILITY("ONSITE_ELIGIBILITY_CHANGED", "核对现场资格"),
    /**
     * 预约上门。
     */
    BOOK_ONSITE("ONSITE_BOOKED", "预约上门"),
    /**
     * 取消预约。
     */
    CANCEL_BOOKING("ONSITE_BOOKING_CANCELLED", "取消预约"),
    /**
     * 登记履约。
     */
    FINISH_BOOKING("ONSITE_FINISHED", "登记履约"),
    /**
     * 安排现场人员。
     */
    ASSIGN_BOOKING("ONSITE_ASSIGNED", "安排现场人员");
    /**
     * 时间线稳定事件类型。
     */
    private final String eventType;
    /**
     * 动作中文含义。
     */
    private final String desc;

    /**
     * 关联动作与事件。
     */
    TicketActionTypeEnum(String eventType, String desc) {
        this.eventType = eventType;
        this.desc = desc;
    }

    /**
     * 返回动作线值。
     */
    @Override
    public String getValue() {
        return name();
    }

    /**
     * 返回中文含义。
     */
    @Override
    public String getDesc() {
        return desc;
    }

    /**
     * 将已登记的人工事件映射为统一动作，未知事件拒绝写回执。
     */
    public static TicketActionTypeEnum fromEvent(String eventType) {
        for (TicketActionTypeEnum action : values()) if (action.eventType.equals(eventType)) return action;
        throw new IllegalArgumentException("未登记的人工动作事件");
    }
}
