package com.campus.user.dto;

import com.campus.common.validation.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改密码请求，需提供旧密码确认身份 */
@Data
public class ChangePasswordReq {

    /** 旧密码，服务端比对 BCrypt 哈希确认身份 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码明文，8-20 位且至少三类字符，入库时加密存储 */
    @NotBlank(message = "新密码不能为空")
    @PasswordPolicy
    private String newPassword;
}
