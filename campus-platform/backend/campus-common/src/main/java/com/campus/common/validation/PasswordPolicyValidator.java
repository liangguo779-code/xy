package com.campus.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * {@link PasswordPolicy} 的校验实现。
 *
 * <p>规则：长度区间、四类字符（大写/小写/数字/特殊）至少满足 N 类、
 * UTF-8 字节数 ≤72（BCrypt 有效上限，超出部分被截断会造成两个不同密码等价）、
 * 不在常见弱口令黑名单。
 */
public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

    private int min;
    private int max;
    private int requiredClasses;

    /** 常见弱口令黑名单（大小写不敏感）；覆盖 top 系列与校园场景高频口令 */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "123456", "12345678", "123456789", "1234567890", "password", "password1",
            "111111", "000000", "666666", "888888", "654321", "123123", "123321",
            "a123456", "a1234567", "abc123", "abcd1234", "qwer1234", "qwerty",
            "qwerty123", "1qaz2wsx", "qazwsx", "asdfgh", "zxcvbn", "asdf1234",
            "admin", "admin123", "admin1234", "administrator", "root", "root123",
            "iloveyou", "woaini", "woaini1314", "5201314", "52013140", "1314520",
            "147258369", "987654321", "112233", "121212", "11111111", "00000000",
            "letmein", "welcome", "welcome1", "monkey", "dragon", "sunshine",
            "princess", "football", "baseball", "master", "shadow", "superman",
            "test1234", "test123456", "passw0rd", "p@ssw0rd", "p@ssword", "aaa123",
            "aa123456", "q123456", "q1234567", "123qwe", "1q2w3e4r", "1q2w3e4r5t",
            "changeme", "student", "student123", "campus", "campus123", "guest",
            "123abc", "abc123456", "7758521", "1111111111", "123456a"
    );

    @Override
    public void initialize(PasswordPolicy annotation) {
        this.min = annotation.min();
        this.max = annotation.max();
        this.requiredClasses = annotation.classes();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // 空值交给 @NotBlank 判定，这里放行避免双重报错
        if (password == null || password.isEmpty()) {
            return true;
        }

        // 1. 长度（按字符数）
        if (password.length() < min || password.length() > max) {
            setMessage(context, "密码长度需 " + min + "-" + max + " 位");
            return false;
        }

        // 2. 字节数上限：BCrypt 只处理前 72 字节，超出会让两个不同密码等价（碰撞）
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            setMessage(context, "密码过长（UTF-8 不得超过 72 字节）");
            return false;
        }

        // 3. 字符类别：大写 / 小写 / 数字 / 其余均视为特殊字符，至少满足 requiredClasses 类
        boolean lower = false, upper = false, digit = false, special = false;
        for (char c : password.toCharArray()) {
            if (c >= 'a' && c <= 'z') lower = true;
            else if (c >= 'A' && c <= 'Z') upper = true;
            else if (c >= '0' && c <= '9') digit = true;
            else special = true;
        }
        int classes = (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
        if (classes < requiredClasses) {
            setMessage(context, "密码需至少包含大写字母、小写字母、数字、特殊字符中的 " + requiredClasses + " 类");
            return false;
        }

        // 4. 弱口令黑名单（大小写不敏感）
        if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
            setMessage(context, "该密码过于常见，请更换");
            return false;
        }

        return true;
    }

    /** 覆盖默认提示语，把具体原因带回前端 */
    private void setMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
