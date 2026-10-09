package com.campus.user.dto;

import com.campus.common.validation.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 密码重置请求，通过邮箱验证码验证身份后重设密码 */
@Data
public class ResetPasswordReq {

    /** 注册邮箱（即登录用户名） */
    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 6 位数字重置验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 重置后的新密码明文，8-20 位且至少三类字符，入库时加密存储 */
    @NotBlank(message = "新密码不能为空")
    @PasswordPolicy
    private String newPassword;
}
