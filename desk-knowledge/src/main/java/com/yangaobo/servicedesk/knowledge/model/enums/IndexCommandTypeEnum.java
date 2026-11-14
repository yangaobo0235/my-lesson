package com.yangaobo.servicedesk.knowledge.model.enums;

/**
 * 仅经原生事务消息传递的指令，保持数据库和接口的稳定值。
 *
 * @author yangaobo
 */
public enum IndexCommandTypeEnum implements com.yangaobo.servicedesk.common.base.BaseEnum<String> {
    /** 提交索引尝试。 */
    SUBMIT("SUBMIT","提交索引尝试"),
    /** 取消原尝试。 */
    CANCEL("CANCEL","取消原尝试");

    /** 稳定线值。 */
    private final String value;
    /** 中文含义。 */
    private final String desc;
    /** 将线值与业务含义绑定，枚举不改变全局JSON行为。 */
    IndexCommandTypeEnum(String value,String desc){this.value=value;this.desc=desc;}
    /** {@inheritDoc} */
    @Override
    public String getValue(){return value;}
    /** {@inheritDoc} */
    @Override
    public String getDesc(){return desc;}
}
