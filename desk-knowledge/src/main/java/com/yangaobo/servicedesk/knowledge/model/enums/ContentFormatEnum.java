package com.yangaobo.servicedesk.knowledge.model.enums;

/**
 * 不可变原件格式，保持数据库和接口的稳定值。
 *
 * @author yangaobo
 */
public enum ContentFormatEnum implements com.yangaobo.servicedesk.common.base.BaseEnum<String> {
    /** PDF原件。 */
    PDF("PDF","PDF原件"),
    /** UTF-8 Markdown原件。 */
    MARKDOWN("MARKDOWN","UTF-8 Markdown原件");

    /** 稳定线值。 */
    private final String value;
    /** 中文含义。 */
    private final String desc;
    /** 将线值与业务含义绑定，枚举不改变全局JSON行为。 */
    ContentFormatEnum(String value,String desc){this.value=value;this.desc=desc;}
    /** {@inheritDoc} */
    @Override
    public String getValue(){return value;}
    /** {@inheritDoc} */
    @Override
    public String getDesc(){return desc;}
}
