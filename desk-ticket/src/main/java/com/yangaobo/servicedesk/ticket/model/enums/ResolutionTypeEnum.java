package com.yangaobo.servicedesk.ticket.model.enums;

import com.yangaobo.servicedesk.common.base.BaseEnum;

/**
 * 人工处理结果，明确保持数据库与接口的稳定线值。
 *
 * @author yangaobo
 */
public enum ResolutionTypeEnum implements BaseEnum<String> {
    /** 人工报告恢复。 */
    RESTORED("RESTORED", "人工报告恢复"),
    /** 已交付指导。 */
    GUIDANCE("GUIDANCE", "已交付指导"),
    /** 转介其他支持。 */
    REFERRED("REFERRED", "转介其他支持"),
    /** 历史结果未知。 */
    LEGACY_UNKNOWN("LEGACY_UNKNOWN", "历史结果未知");

    /** 稳定线值。 */
    private final String value;

    /** 中文含义。 */
    private final String desc;

    /** 关联线值和中文含义。 */
    ResolutionTypeEnum(String value, String desc) {
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
