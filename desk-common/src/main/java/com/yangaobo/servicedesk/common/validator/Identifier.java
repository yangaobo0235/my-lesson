package com.yangaobo.servicedesk.common.validator;

import com.yangaobo.servicedesk.common.constant.IdentifierConst;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验可空标识，必填字段另外声明NotBlank。
 *
 * @author yangaobo
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = IdentifierConst.PATTERN)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Identifier {
    /** 格式拒绝提示。 */
    String message() default "标识必须为32位小写十六进制";

    /** 校验分组。 */
    Class<?>[] groups() default {};

    /** 校验附加元数据。 */
    Class<? extends Payload>[] payload() default {};
}
