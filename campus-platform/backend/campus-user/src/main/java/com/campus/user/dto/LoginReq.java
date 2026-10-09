package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 账号密码登录请求 */
@Data
public class LoginReq {

    /** 用户名（本系统中即注册邮箱） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码明文，服务端比对 BCrypt 哈希 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码 ID（登录失败达到阈值后必填，未触发时忽略） */
    private String captchaId;

    /** 图形验证码答案（登录失败达到阈值后必填） */
    private String captchaAnswer;

    /** 记住我：延长 token 有效期（30 天），默认否 */
    private Boolean rememberMe;
}
