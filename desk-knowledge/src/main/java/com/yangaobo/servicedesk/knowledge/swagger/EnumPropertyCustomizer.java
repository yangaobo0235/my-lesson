package com.yangaobo.servicedesk.knowledge.swagger;

import com.fasterxml.jackson.databind.JavaType;
import com.yangaobo.servicedesk.common.annotation.SchemaEnum;
import com.yangaobo.servicedesk.common.base.BaseEnum;
import com.yangaobo.servicedesk.common.constant.IdentifierConst;
import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;

/**
 * 将枚举的稳定值与中文含义写入字段 Schema，不修改运行时 JSON 或数据库映射。
 *
 * @author yangaobo
 */
public class EnumPropertyCustomizer implements PropertyCustomizer, OpenApiCustomizer {

    /**
     * 文档允许值读取 BaseEnum.getValue；共享枚举保持该值与名称一致。
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Schema customize(Schema property, AnnotatedType type) {
        if (property == null) return null;
        Class<?> rawType =
                type.getType() instanceof JavaType javaType
                        ? javaType.getRawClass()
                        : type.getType() instanceof Class<?> raw ? raw : null;
        Class<?> enumType = rawType != null && rawType.isEnum() ? rawType : null;
        Annotation[] annotations = type.getCtxAnnotations();
        Pattern allowed = null;
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof SchemaEnum source) enumType = source.value();
                if (annotation instanceof jakarta.validation.constraints.Pattern constraint)
                    allowed = Pattern.compile(constraint.regexp());
                if (annotation instanceof Identifier) property.setPattern(IdentifierConst.PATTERN);
            }
        }
        if (enumType == null || !BaseEnum.class.isAssignableFrom(enumType)) return property;
        List<String> values = new ArrayList<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Object item : enumType.getEnumConstants()) {
            String name = String.valueOf(((BaseEnum<?>) item).getValue());
            if (allowed != null && !allowed.matcher(name).matches()) continue;
            values.add(name);
            descriptions.put(name, ((BaseEnum<?>) item).getDesc());
        }
        property.setType("string");
        property.setEnum(values);
        property.addExtension("x-enum-descriptions", descriptions);
        return property;
    }

    /**
     * 在字段注解合并完成后补齐枚举中文说明，避免被 Schema.description 覆盖。
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void customise(OpenAPI api) {
        if (api.getComponents() == null || api.getComponents().getSchemas() == null) return;
        Set<Schema> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        api.getComponents().getSchemas().values().forEach(schema -> describe(schema, visited));
    }

    /**
     * 遍历组合、集合及嵌套字段；引用由 components 中的对应 Schema 单独处理。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void describe(Schema schema, Set<Schema> visited) {
        if (schema == null || !visited.add(schema)) return;
        Object extension =
                schema.getExtensions() == null
                        ? null
                        : schema.getExtensions().get("x-enum-descriptions");
        if (extension instanceof Map<?, ?> descriptions) {
            String suffix =
                    String.join(
                            "；",
                            descriptions.entrySet().stream()
                                    .map(entry -> entry.getKey() + "：" + entry.getValue())
                                    .toList());
            String current = schema.getDescription();
            if (current == null || !current.contains(suffix)) {
                schema.setDescription((current == null ? "" : current + "；") + suffix);
            }
        }
        if (schema.getProperties() != null)
            schema.getProperties().values().forEach(item -> describe((Schema) item, visited));
        describe(schema.getItems(), visited);
        if (schema.getAdditionalProperties() instanceof Schema item) describe(item, visited);
        for (List<Schema> composition :
                List.of(
                        schema.getAllOf() == null ? List.<Schema>of() : schema.getAllOf(),
                        schema.getAnyOf() == null ? List.<Schema>of() : schema.getAnyOf(),
                        schema.getOneOf() == null ? List.<Schema>of() : schema.getOneOf())) {
            composition.forEach(item -> describe(item, visited));
        }
    }
}
