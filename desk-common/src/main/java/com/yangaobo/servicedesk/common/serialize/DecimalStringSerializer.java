package com.yangaobo.servicedesk.common.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 仅用于明确声明的版本与计数字段。
 *
 * @author yangaobo
 */
public final class DecimalStringSerializer extends JsonSerializer<Long> {
    /** 将非负版本及计数输出为十进制字符串，避免浏览器精度丢失。 */
    @Override
    public void serialize(Long value, JsonGenerator generator, SerializerProvider provider)
            throws IOException {
        generator.writeString(value.toString());
    }
}
