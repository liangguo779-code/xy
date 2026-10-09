package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录审计日志，对应 login_log 表。
 * 成功与失败（含被锁定、验证码错误）均落库，用于暴力破解追溯与异常登录排查。
 */
@Data
@TableName("login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 尝试登录的用户名（即邮箱），无论成败都记录 */
    private String username;

    /** 登录成功的用户 ID，失败时为空 */
    private Long userId;

    /** 客户端 IP */
    private String ip;

    /** 浏览器 User-Agent */
    private String userAgent;

    /** 1-成功 0-失败 */
    private Integer success;

    /** 结果说明（用户名或密码错误/账号已被禁用/登录尝试次数过多等） */
    private String message;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
