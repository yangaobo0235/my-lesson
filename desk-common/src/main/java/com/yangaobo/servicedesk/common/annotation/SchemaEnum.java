package com.yangaobo.servicedesk.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明文档枚举来源，不负责输入校验或JSON序列化。
 *
 * @author yangaobo
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface SchemaEnum {
    /**
     * 返回实现BaseEnum的本地或公开合同枚举类型。
     */
    Class<? extends Enum<?>> value();
}
