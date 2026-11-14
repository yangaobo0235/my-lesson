package com.yangaobo.servicedesk.ticket.model.enums;

/**
 * 区域时段生命周期，保持数据库和接口的稳定值。
 *
 * @author yangaobo
 */
public enum OnsiteSlotStatusEnum implements com.yangaobo.servicedesk.common.base.BaseEnum<String> {
    /** 待核对发布。 */
    DRAFT("DRAFT","待核对发布"),
    /** 已发布。 */
    PUBLISHED("PUBLISHED","已发布"),
    /** 已关停。 */
    CLOSED("CLOSED","已关停");

    /** 稳定线值。 */
    private final String value;
    /** 中文含义。 */
    private final String desc;
    /** 将线值与业务含义绑定，枚举不改变全局JSON行为。 */
    OnsiteSlotStatusEnum(String value,String desc){this.value=value;this.desc=desc;}
    /** {@inheritDoc} */
    @Override
    public String getValue(){return value;}
    /** {@inheritDoc} */
    @Override
    public String getDesc(){return desc;}
}
