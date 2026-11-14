package com.yangaobo.servicedesk.ticket.model.enums;

/**
 * 人工登记的真实到访结果，保持数据库和接口的稳定值。
 *
 * @author yangaobo
 */
public enum OnsiteFinishResultEnum implements com.yangaobo.servicedesk.common.base.BaseEnum<String> {
    /** 实际已到访。 */
    VISITED("VISITED","实际已到访"),
    /** 实际未履约。 */
    NOT_PERFORMED("NOT_PERFORMED","实际未履约");

    /** 稳定线值。 */
    private final String value;
    /** 中文含义。 */
    private final String desc;
    /** 将线值与业务含义绑定，枚举不改变全局JSON行为。 */
    OnsiteFinishResultEnum(String value,String desc){this.value=value;this.desc=desc;}
    /** {@inheritDoc} */
    @Override
    public String getValue(){return value;}
    /** {@inheritDoc} */
    @Override
    public String getDesc(){return desc;}
}
