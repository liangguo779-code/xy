package com.campus.user.dto;

import com.campus.common.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 邮箱注册请求，注册前需先完成邮箱验证码校验 */
@Data
public class RegisterReq {

    /** 注册邮箱，同时作为登录用户名 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 6 位数字邮箱验证码，需与 Redis 中缓存值一致 */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String code;

    /** 登录密码明文，8-20 位且至少三类字符，入库时加密存储 */
    @NotBlank(message = "密码不能为空")
    @PasswordPolicy
    private String password;

    /** 昵称，不填则默认取邮箱 @ 前缀 */
    private String nickname;
}
