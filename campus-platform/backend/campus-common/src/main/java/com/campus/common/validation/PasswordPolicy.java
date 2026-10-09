package com.campus.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 密码强度校验：长度 8-20 位（可调），大写/小写/数字/特殊字符四类至少含三类，
 * UTF-8 字节数不超过 72（防 BCrypt 首 72 字节截断导致的密码碰撞），
 * 且不得命中常见弱口令黑名单。
 *
 * <p>用法：{@code @PasswordPolicy(min = 12)} 用于管理员等高敏场景收紧下限。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface PasswordPolicy {

    String message() default "密码需8-20位，且至少包含大写字母、小写字母、数字、特殊字符中的三类";

    /** 最小长度（默认 8） */
    int min() default 8;

    /** 最大长度（默认 20） */
    int max() default 20;

    /** 需要满足的字符类别数（默认 3） */
    int classes() default 3;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
